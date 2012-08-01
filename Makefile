all:
	jekyll src out

serve:
	jekyll --serve --auto src out

publish: all
	./publish.sh out

.PHONY: all serve publish
