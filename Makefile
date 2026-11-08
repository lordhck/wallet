SHELL := /bin/bash

NAME := BTC Wallet
DESC := build script
IMAGE := android
TAG := latest
DOCKERFILE := docker/Dockerfile
GRADLE_VOLUME := wallet-gradle
# .android keeps the debug signing key between builds. Without it each build
# gets a new key and the phone refuses to install it as an update.
RUN := docker run --rm -v "$(PWD):/workspace" -v $(GRADLE_VOLUME):/home/xyz/.gradle -v "$(PWD)/.android:/home/xyz/.android"

.PHONY: image build shell clean help

image: ## Build Docker image
	docker buildx build --load -t $(IMAGE):$(TAG) -f $(DOCKERFILE) .

build: ## Build debug APK
	@mkdir -p .android
	$(RUN) $(IMAGE):$(TAG) ./gradlew assembleDebug

shell: ## Open container shell
	@mkdir -p .android
	$(RUN) -it $(IMAGE):$(TAG)

clean: ## Remove image and cache
	-docker volume rm $(GRADLE_VOLUME)
	docker image rm $(IMAGE):$(TAG)

help: ## Show this help
	@if [[ -t 1 && -z "$${NO_COLOR:-}" ]]; then \
		B=$$'\033[38;2;122;162;247m' D=$$'\033[2m' H=$$'\033[1m' R=$$'\033[0m'; \
	else B= D= H= R=; fi; \
	printf '%s$(NAME)%s - $(DESC)\n\n' "$$H" "$$R"; \
	printf 'Usage: make %s<target>%s [VAR=value]\n\n' "$$B" "$$R"; \
	printf '%sTargets%s\n' "$$H" "$$R"; \
	grep -hE '^[a-zA-Z0-9_.-]+:.*## ' $(MAKEFILE_LIST) | while IFS= read -r l; do \
		printf '  %s%-12s%s %s\n' "$$B" "$${l%%:*}" "$$R" "$${l#*## }"; done
