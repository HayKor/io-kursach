// =============================================================
//  report.typ — общая обёртка для всех собираемых документов
//
//  Каждая точка входа (index.typ, practices_*.typ) применяет её
//  через `#show: report.with(...)` и затем подключает главы.
// =============================================================

#import "settings.typ": apply-gost, contents, title-page

#let meta = (
  topic:  "Система учёта дисциплинарных взысканий и замечаний",
  author: "Багинян Артур Варданович",
  date:   datetime(year: 2026, month: 9, day: 22),
)

#let report(
  title: meta.topic,
  body,
) = {
  set document(title: title, author: meta.author, date: meta.date)
  show: apply-gost

  contents()

  body
}
