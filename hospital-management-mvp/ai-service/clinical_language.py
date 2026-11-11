"""Normalize common Romanian symptom wording to terms already present in TRAIN."""
import re
from medications import normalize
PHRASES={
 'durere la urinare':'disurie','dureri la urinare':'disurie','usturime la urinare':'disurie','ma ustura cand urinez':'disurie','usturime cand urinez':'disurie',
 'urinez des':'polakiurie','urinari dese':'polakiurie','urinari frecvente':'polakiurie','urina tulbure':'urina tulbure relatata',
 'arsuri dupa masa':'arsuri postprandiale','arsuri dupa mese':'arsuri postprandiale','regurgitatii acide':'regurgitatie acida','acid in gura':'regurgitatie acida',
 'imi curge nasul':'rinoree','mancarime nazala':'mancarime nazala','ma mananca nasul':'mancarime nazala',
 'ma mananca pielea':'prurit local','mancarime a pielii':'prurit local','ma doare capul bilateral':'durere de cap bilaterala',
 'nu pot sa dorm':'dificultate de adormire','scaun rar':'scaune rare',
}
def clinical_language(text):
 result=normalize(text)
 for source,target in sorted(PHRASES.items(),key=lambda entry:-len(entry[0])):
  result=re.sub(r'(?<!\w)'+re.escape(source)+r'(?!\w)',target,result)
 return result
