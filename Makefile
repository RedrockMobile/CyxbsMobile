.DEFAULT_GOAL := debug
.NOTPARALLEL:
.PHONY: debug install_and_run

# Windows: native Python, not WSL Python. Override with PYTHON="py -3" if needed.
ifeq ($(OS),Windows_NT)
PYTHON ?= python
else
PYTHON ?= python3
endif

CONSOLE ?= auto
SERIAL ?=
export CONSOLE SERIAL

debug:
	@$(PYTHON) scripts/android.py debug

install_and_run:
	@$(PYTHON) scripts/android.py install_and_run
