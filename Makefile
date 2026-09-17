.PHONY: test demo benchmark clean install

test:
	python3 -m unittest test_anima.py

demo:
	@echo "==> Running Cold Compilation Run..."
	python3 anima.py "toggle wifi" --mock
	@echo "\n==> Running Warm 0-LLM Speculative Replay..."
	python3 anima.py "toggle wifi" --mock

clean:
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true
	rm -f *.db 2>/dev/null || true

install:
	pip install -e .
