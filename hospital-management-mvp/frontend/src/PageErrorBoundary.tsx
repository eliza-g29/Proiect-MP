import { Component, type ErrorInfo, type ReactNode } from 'react';

type Props = { children: ReactNode };
type State = { hasError: boolean };

export default class PageErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[MedFlow] Eroare la afișarea paginii', error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return <section className="panel modal-form" role="alert">
        <h2>Pagina nu s-a putut afișa</h2>
        <p>Poți reîncerca afișarea sau poți alege altă secțiune din meniul lateral.</p>
        <button type="button" className="btn primary-btn" onClick={() => this.setState({ hasError: false })}>
          Reîncearcă afișarea
        </button>
      </section>;
    }
    return this.props.children;
  }
}
