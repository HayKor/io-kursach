# =============================================================
#  Сборка документов курсовой
#
#  make            — собрать всё (build/*.pdf)
#  make full       — итоговый документ        (src/index.typ)
#  make p12        — практики 1–2              (src/practices_1-2.typ)
#  make p34        — практики 3–4              (src/practices_3-4.typ)
#  make watch-p34  — пересборка при изменениях (также watch-p12, watch-full)
#  make diagrams   — экспорт схем draw.io в src/img/*.pdf (делается и само)
#  make release    — скопировать PDF в output/ с именами для сдачи
#  make clean      — удалить собранное
# =============================================================

SRC    := src
BUILD  := build
OUTPUT := output

TYPST  := typst
TFLAGS := --root $(SRC)

# цель -> имя точки входа в src/ (и PDF в build/)
DOC_full := index
DOC_p12  := practices_1-2
DOC_p34  := practices_3-4
TARGETS  := full p12 p34

# имена файлов для сдачи
REL_full := Багинян_А_В_дисц-взыскания.pdf
REL_p12  := Багинян_А_В_практики_1-2.pdf
REL_p34  := Багинян_А_В_практики_3-4.pdf

PDFS := $(foreach t,$(TARGETS),$(BUILD)/$(DOC_$(t)).pdf)

# схемы draw.io: каждая вкладка -> src/img/<имя вкладки>.pdf
DIAGRAMS   := diagrams/practices_3-4.drawio
IMG        := $(SRC)/img
DIAG_STAMP := $(BUILD)/diagrams.stamp

.PHONY: all clean release diagrams $(TARGETS) $(addprefix watch-,$(TARGETS))

all: $(PDFS)

$(foreach t,$(TARGETS),$(eval $(t): $(BUILD)/$(DOC_$(t)).pdf))

# Typst сам пишет точный список зависимостей (.d), включая главы,
# include-файлы и картинки — пересборка только при реальных изменениях.
$(BUILD)/%.pdf: $(SRC)/%.typ | $(BUILD)
	$(TYPST) compile $(TFLAGS) --deps $(BUILD)/$*.d --deps-format make $< $@

-include $(wildcard $(BUILD)/*.d)

# документы со схемами пересобираются после их экспорта
$(BUILD)/$(DOC_full).pdf $(BUILD)/$(DOC_p34).pdf: $(DIAG_STAMP)

diagrams: $(DIAG_STAMP)

$(DIAG_STAMP): $(DIAGRAMS) diagrams/export.sh | $(BUILD)
	./diagrams/export.sh $(DIAGRAMS) $(IMG)
	touch $@

$(foreach t,$(TARGETS),$(eval watch-$(t): ; \
	$(TYPST) watch $(TFLAGS) $(SRC)/$(DOC_$(t)).typ $(BUILD)/$(DOC_$(t)).pdf))

release: $(PDFS) | $(OUTPUT)
	$(foreach t,$(TARGETS),cp $(BUILD)/$(DOC_$(t)).pdf "$(OUTPUT)/$(REL_$(t))";)

$(BUILD) $(OUTPUT):
	mkdir -p $@

clean:
	rm -f $(BUILD)/*.pdf $(BUILD)/*.d $(DIAG_STAMP)
	$(foreach t,$(TARGETS),rm -f "$(OUTPUT)/$(REL_$(t))";)
