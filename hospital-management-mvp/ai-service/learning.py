"""Portable TF-IDF and multinomial logistic regression for synthetic demonstrations.

Training uses sparse SGD; runtime weights are JSON, never executable pickle files.
Only the Python standard library is required for training and inference.
"""
from collections import Counter
import math
import random
import re
import unicodedata

TOKEN = re.compile(r"(?u)\b\w\w+\b")


def features(text):
    normalized = "".join(c for c in unicodedata.normalize("NFKD", text.lower()) if not unicodedata.combining(c))
    words = TOKEN.findall(normalized)
    return Counter(words + [a + " " + b for a, b in zip(words, words[1:])])


class Tfidf:
    def __init__(self, vocabulary, idf):
        self.vocabulary = vocabulary
        self.idf = idf

    @classmethod
    def fit(cls, texts, min_df=2, max_features=12000):
        frequency = Counter()
        for text in texts:
            frequency.update(features(text).keys())
        terms = sorted((term for term, count in frequency.items() if count >= min_df),
                       key=lambda term: (-frequency[term], term))[:max_features]
        terms.sort()
        if not terms:
            raise ValueError("No vocabulary remains after filtering")
        return cls({term: index for index, term in enumerate(terms)},
                   [math.log((1 + len(texts)) / (1 + frequency[term])) + 1 for term in terms])

    def transform(self, text):
        vector = [(self.vocabulary[term], (1 + math.log(count)) * self.idf[self.vocabulary[term]])
                  for term, count in features(text).items() if term in self.vocabulary]
        norm = math.sqrt(sum(value * value for _, value in vector))
        return [(index, value / norm) for index, value in vector] if norm else []

    def to_dict(self):
        return {"vocabulary": self.vocabulary, "idf": self.idf}

    @classmethod
    def from_dict(cls, data):
        return cls(data["vocabulary"], data["idf"])


def softmax(scores):
    maximum = max(scores)
    exponents = [math.exp(value - maximum) for value in scores]
    total = sum(exponents)
    return [value / total for value in exponents]


class LogisticModel:
    def __init__(self, classes, weights, bias):
        self.classes = classes
        self.weights = weights
        self.bias = bias

    def probabilities(self, vector):
        return softmax([bias + sum(row[index] * value for index, value in vector)
                        for row, bias in zip(self.weights, self.bias)])

    def predict(self, vector):
        probabilities = self.probabilities(vector)
        index = max(range(len(probabilities)), key=probabilities.__getitem__)
        return self.classes[index], probabilities[index]

    def to_dict(self):
        return {"classes": self.classes, "weights": self.weights, "bias": self.bias}

    @classmethod
    def from_dict(cls, data):
        return cls(data["classes"], data["weights"], data["bias"])


def fit_logistic(train_vectors, train_labels, validation_vectors, validation_labels,
                 feature_count, *, epochs=40, learning_rate=0.35, l2=0.0002,
                 seed=42, patience=6, progress=None):
    """Minimize weighted cross entropy + L2, selecting an epoch by validation loss.

    L2 is applied through a lazy global weight scale; intercepts are not regularized.
    Test examples must never be passed to this function.
    """
    classes = sorted(set(train_labels))
    lookup = {label: i for i, label in enumerate(classes)}
    targets = [lookup[label] for label in train_labels]
    class_counts = Counter(train_labels)
    class_weights = {label: len(train_labels) / (len(classes) * count) for label, count in class_counts.items()}
    weights = [[0.0] * feature_count for _ in classes]
    bias = [0.0] * len(classes)
    scale = 1.0
    generator = random.Random(seed)
    order = list(range(len(train_vectors)))
    best_loss = math.inf
    best = None
    best_epoch = 0
    stale = 0
    history = []
    for epoch in range(1, epochs + 1):
        generator.shuffle(order)
        rate = learning_rate / math.sqrt(1 + (epoch - 1) / 10)
        for example in order:
            vector = train_vectors[example]
            scores = [offset + scale * sum(row[index] * value for index, value in vector)
                      for row, offset in zip(weights, bias)]
            probabilities = softmax(scores)
            scale *= 1 - rate * l2
            step = rate * class_weights[train_labels[example]]
            for label_index, probability in enumerate(probabilities):
                gradient = step * (probability - (label_index == targets[example]))
                bias[label_index] -= gradient
                delta = gradient / scale
                row = weights[label_index]
                for index, value in vector:
                    row[index] -= delta * value
            if scale < 0.01:
                weights = [[value * scale for value in row] for row in weights]
                scale = 1.0
        current = LogisticModel(classes, [[value * scale for value in row] for row in weights], bias.copy())
        validation_loss = -sum(math.log(max(1e-15, current.probabilities(vector)[lookup[label]]))
                               for vector, label in zip(validation_vectors, validation_labels)) / len(validation_vectors)
        history.append({"epoch": epoch, "validation_log_loss": validation_loss, "learning_rate": rate})
        if progress:
            progress(epoch, validation_loss)
        if validation_loss < best_loss - 1e-5:
            best_loss = validation_loss
            best = current
            best_epoch = epoch
            stale = 0
        else:
            stale += 1
            if stale >= patience:
                break
    if best is None:
        raise ValueError("Training did not produce a model")
    return best, {"best_epoch": best_epoch, "epochs_run": len(history),
                  "best_validation_log_loss": best_loss, "history": history,
                  "learning_rate": learning_rate, "l2": l2, "seed": seed,
                  "class_weight": "balanced", "selection": "validation log loss only"}


def classification_metrics(expected, predicted, classes):
    positions = {label: i for i, label in enumerate(classes)}
    matrix = [[0] * len(classes) for _ in classes]
    for truth, estimate in zip(expected, predicted):
        matrix[positions[truth]][positions[estimate]] += 1
    per_class = {}
    for label, index in positions.items():
        true_positive = matrix[index][index]
        support = sum(matrix[index])
        predicted_count = sum(row[index] for row in matrix)
        precision = true_positive / predicted_count if predicted_count else 0.0
        recall = true_positive / support if support else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        per_class[label] = {"precision": precision, "recall": recall, "f1": f1, "support": support}
    total = len(expected)
    return {"examples": total, "accuracy": sum(matrix[i][i] for i in range(len(classes))) / total,
            "macro_f1": sum(row["f1"] for row in per_class.values()) / len(classes),
            "weighted_f1": sum(row["f1"] * row["support"] for row in per_class.values()) / total,
            "classes": classes, "per_class": per_class, "confusion_matrix": matrix}
