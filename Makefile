
IMAGE := android
TAG := latest
DOCKERFILE := docker/Dockerfile

.PHONY: image shell clean help

image:
	docker buildx build --load -t $(IMAGE):$(TAG) -f $(DOCKERFILE) .

shell:
	docker run --rm -it \
		-v "$(PWD):/workspace" \
		$(IMAGE):$(TAG)

clean:
	docker image rm $(IMAGE):$(TAG)

help:
	@echo "Android Docker Builder"
	@echo ""
	@echo "  make image  Build Docker image"
	@echo "  make shell  Open container shell"
	@echo "  make clean  Remove Docker image"
	@echo "  make help   Show this help"
