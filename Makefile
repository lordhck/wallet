
IMAGE := android
TAG := latest
DOCKERFILE := docker/Dockerfile
GRADLE_VOLUME := wallet-gradle

.PHONY: image shell clean help

image:
	docker buildx build --load -t $(IMAGE):$(TAG) -f $(DOCKERFILE) .

shell:
	docker run --rm -it \
		-v "$(PWD):/workspace" \
		-v $(GRADLE_VOLUME):/home/xyz/.gradle \
		$(IMAGE):$(TAG)

clean:
	-docker volume rm $(GRADLE_VOLUME)
	docker image rm $(IMAGE):$(TAG)

help:
	@echo "Android Docker Builder"
	@echo ""
	@echo "  make image  Build Docker image"
	@echo "  make shell  Open container shell"
	@echo "  make clean  Remove Docker image and Gradle cache"
	@echo "  make help   Show this help"
