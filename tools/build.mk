
# NOTE: This file is included into the parent directory's Makefile; therefore,
#       all paths must be prefixed with a "tools/".

# HACK: Make's syntax is... simplicistic.
SP := $(subst ,, )

TOOL_NAMES := $(filter-out tools/build.mk tools/transclude.conf tools/build \
    tools/%.jar,$(wildcard tools/*))
TOOL_ARCHIVES := $(patsubst %,%.jar,$(TOOL_NAMES))
TOOL_CLASSPATH := $(subst $(SP),:,$(patsubst tools/%,../%, \
    $(TOOL_NAMES))):../../src

.PHONY: tools/_clean tools/_pre-commit

.SECONDARY:
.DELETE_ON_ERROR:
.SECONDEXPANSION:

all: $(TOOL_ARCHIVES)
clean: tools/_clean
pre-commit: tools/_pre-commit

tools/build:
	mkdir $@

tools/build/%.jar: $$(shell find tools/$$* -name '*.java' 2>/dev/null) | \
    tools/build
	find tools/$* -name '*.class' -exec rm {} +
	cd tools/$* && find . -name '*.java' -print0 | xargs -0r \
	    javac -cp $(CLASSPATH):$(TOOL_CLASSPATH) $(JAVACFLAGS)
	cd tools/$* && jar cf ../build/$*.jar META-INF/MANIFEST.MF \
	    $$(find . -name '*.class')

tools/%.jar: tools/build/%.jar $$(shell find tools/$$* -type f 2>/dev/null)
	cp tools/build/$*.jar tools/$*.jar
	cd tools/$* && jar uf ../$*.jar $$(find . -type f -not -path \
	    './META-INF/MANIFEST.MF')
	script/transclude.py --config tools/transclude.conf --jar $@
	cd tools/$* && [ -f META-INF/MANIFEST.MF ] && \
	    jar ufm ../$*.jar META-INF/MANIFEST.MF || true

tools/_clean:
	rm -rf tools/build/

tools/_pre-commit:
	@git add $(TOOL_ARCHIVES)