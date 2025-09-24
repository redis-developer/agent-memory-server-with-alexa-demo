#!/bin/bash

### Check if skill is already deployed

echo "🔍 Checking if Alexa skill is already deployed..."

if [ -f ".ask/ask-states.json" ]; then
    SKILL_ID=$(jq -r '.profiles.default.skillId' .ask/ask-states.json 2>/dev/null)

    if [ ! -z "$SKILL_ID" ] && [ "$SKILL_ID" != "null" ]; then
        echo "✅ Skill already deployed with ID: $SKILL_ID"

        # Optional: Verify the skill actually exists in your account
        if command -v ask &> /dev/null; then
            if ask smapi get-skill-manifest -s "$SKILL_ID" --profile default &> /dev/null; then
                echo "✅ Skill verified in Alexa account"
            else
                echo "⚠️ Skill ID found locally but not in Alexa account. Redeploying..."
                SKILL_ID=""
            fi
        fi
    else
        SKILL_ID=""
    fi
else
    SKILL_ID=""
fi

### Deploy the skill (only if not already deployed)

if [ -z "$SKILL_ID" ]; then
    echo "⚙️ Deploying the Alexa skill for the first time"
    ask deploy --profile default

    SKILL_ID=$(jq -r '.profiles.default.skillId' .ask/ask-states.json)

    if [ -z "$SKILL_ID" ] || [ "$SKILL_ID" = "null" ]; then
        echo "Error: Could not read skill ID from .ask/ask-states.json"
        exit 1
    fi
else
    echo "⏭️ Skipping initial skill deployment (already exists)"
fi

### deploy the lambda function

echo "⚙️ Deploying the Lambda function"

cd infrastructure

if [ ! -f terraform.tfvars.backup ]; then
  cp terraform.tfvars terraform.tfvars.backup
  if [[ "$OSTYPE" == "darwin"* ]]; then
      # macOS
      sed -i '' "s/alexa_skill_id = \".*\"/alexa_skill_id = \"$SKILL_ID\"/" terraform.tfvars
  else
      # Linux
      sed -i "s/alexa_skill_id = \".*\"/alexa_skill_id = \"$SKILL_ID\"/" terraform.tfvars
  fi
fi

terraform init
terraform apply -auto-approve

LAMBDA_ARN=$(terraform output -raw my_jarvis_alexa_skill_handler_arn)

### Update the skill with the lambda ARN

echo "⚙️ Updating the Alexa skill endpoint"

cd ../

if [ ! -f skill-package/skill.json.backup ]; then
  cp skill-package/skill.json skill-package/skill.json.backup
fi

jq --arg arn "$LAMBDA_ARN" '.manifest.apis.custom.endpoint = {uri: $arn}' skill-package/skill.json > tmp.json && mv tmp.json skill-package/skill.json
ask deploy --profile default

### Finish off the deployment
cd ..
