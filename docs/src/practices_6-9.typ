// Практики 6–9: диаграммы компонентов и развёртывания, схема БД — п. 4.3 ТЗ по ГОСТ 34.602
#import "include/report.typ": *

#show: report.with(title: meta.topic + " — практики 6–9")

// Нумерация разделов как в полном ТЗ: «4 Требования» → «4.3 Требования к видам обеспечения»
#counter(heading).update(3)
= Требования
#counter(heading).update((4, 2))

#include "chapters/05_support.typ"
#include "chapters/08_information.typ"
#include "chapters/06_software.typ"
#include "chapters/07_hardware.typ"
