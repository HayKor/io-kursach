SRC_PREFIX = src/
BUILD_DIR = build/

TYPST_MAIN = $(SRC_PREFIX)index.typ

TEMP_PDF = $(BUILD_DIR)index.pdf

OUTPUT_PDF = Багинян_А_В_дисц-взыскания.pdf

$(TEMP_PDF): $(TYPST_MAIN) $(SRC_PREFIX)include/*.typ $(SRC_PREFIX)chapters/*.typ
	typst compile $(TYPST_MAIN) $(TEMP_PDF)

clean:
	rm -f $(TEMP_PDF) $(OUTPUT_PDF)

watch:
	typst watch $(TYPST_MAIN) $(TEMP_PDF)
