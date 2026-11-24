#!/usr/bin/env bash
# Run this in Google Cloud Shell (https://shell.cloud.google.com).
# The first run creates the free VM and installs the app. Later runs update the app and keep its data.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

VM=lifeline
ZONE=${ZONE:-us-central1-a} # Free regions only: us-west1, us-central1, us-east1

case $ZONE in
  us-west1-* | us-central1-* | us-east1-*) ;;
  *) echo "Stopped: $ZONE is not in a free region (us-west1, us-central1, us-east1)." >&2; exit 1 ;;
esac

project=$(gcloud config get-value project 2>/dev/null || true)
if [[ -z $project || $project == "(unset)" ]]; then
  echo "Choose your project first:  gcloud config set project YOUR_PROJECT_ID" >&2
  exit 1
fi
echo "Project: $project   VM: $VM   Zone: $ZONE"

echo "Turning on Compute Engine (about a minute the first time)..."
gcloud services enable compute.googleapis.com --quiet

if [[ -z $(gcloud compute instances list --filter="name=$VM AND zone:$ZONE" --format="value(name)") ]]; then
  # e2-micro and a standard disk of 30 GB or less are what the free tier covers.
  gcloud compute instances create "$VM" --zone "$ZONE" --machine-type e2-micro \
    --image-family ubuntu-2404-lts-amd64 --image-project ubuntu-os-cloud \
    --boot-disk-type pd-standard --boot-disk-size 20GB \
    --no-service-account --no-scopes --tags lifeline-web --quiet
fi

if [[ -z $(gcloud compute firewall-rules list --filter="name=lifeline-web" --format="value(name)") ]]; then
  gcloud compute firewall-rules create lifeline-web --allow tcp:80,tcp:443 --target-tags lifeline-web --quiet
fi

echo "Waiting for the VM to accept connections..."
for attempt in {1..20}; do
  if gcloud compute ssh "$VM" --zone "$ZONE" --quiet --command true 2>/dev/null </dev/null; then
    break
  fi
  if [[ $attempt == 20 ]]; then
    echo "The VM did not answer. Wait two minutes and run this again." >&2
    exit 1
  fi
  sleep 10
done

echo "Copying the app..."
gcloud compute scp --zone "$ZONE" --quiet lifeline.jar install.sh "$VM:~/"
gcloud compute ssh "$VM" --zone "$ZONE" --quiet --command 'sudo bash ~/install.sh' -- -t
