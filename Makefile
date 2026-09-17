.PHONY: test e2e test-all demo benchmark clean install

test:
	python3 -m unittest test_anima.py

e2e:
	python3 -m unittest test_e2e.py

test-all:
	python3 -m unittest discover -p "test_*.py"

demo:
	python3 anima.py --demo

benchmark:
	python3 anima.py --benchmark

clean:
	find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete 2>/dev/null || true
	rm -f *.db 2>/dev/null || true

install:
	pip install -e .
