
JAVACFLAGS = -Xlint:all -Xlint:-serial -Werror

# HACK: Make's syntax is... simplicistic.
SP := $(subst ,, )

TOOL_NAMES := $(filter-out Makefile build %.jar,$(wildcard *))
TOOL_ARCHIVES := $(patsubst %,%.jar,$(TOOL_NAMES))
TOOL_CLASSPATH := $(subst $(SP),:,$(patsubst %,../%,$(TOOL_NAMES))):../../src

.PHONY: all clean _pre-commit

.SECONDARY:
.DELETE_ON_ERROR:
.SECONDEXPANSION:

all: $(TOOL_ARCHIVES)

clean:
	rm -rf build/

build:
	mkdir $@

build/%.jar: $$(shell find $$* -name '*.java' 2>/dev/null) | build
	find $* -name '*.class' -exec rm {} +
	cd $* && find . -name '*.java' -print0 | xargs -0r \
	    javac -cp $(CLASSPATH):$(TOOL_CLASSPATH) $(JAVACFLAGS)
	cd $* && jar cf ../build/$*.jar META-INF/MANIFEST.MF \
	    $$(find . -name '*.class')

%.jar: build/%.jar $$(shell find $$* -type f 2>/dev/null)
	cp build/$*.jar $*.jar
	cd $* && jar uf ../$*.jar $$(find . -type f -not -path \
	    './META-INF/MANIFEST.MF')
	cd $* && [ -f META-INF/MANIFEST.MF ] && \
	    jar ufm ../$*.jar META-INF/MANIFEST.MF || true

_pre-commit: all
	@git add $(TOOL_ARCHIVES)
