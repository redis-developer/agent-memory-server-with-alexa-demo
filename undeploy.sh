#!/bin/bash

### Undeploy the lambda function

echo "⚙️ Undeploying the Lambda function"

cd infrastructure/terraform

terraform destroy -auto-approve
if [[ -f terraform.tfvars.backup ]]; then
  mv terraform.tfvars.backup terraform.tfvars
fi

### Undeploy the skill

echo "⚙️ Removing the Alexa skill"

cd ../../

if [[ -f .ask/ask-states.json ]]; then
  SKILL_ID=$(jq -r '.profiles.default.skillId' .ask/ask-states.json)
fi

if ask smapi get-skill-status --skill-id ${SKILL_ID} 2>/dev/null; then
    ask smapi delete-skill --skill-id ${SKILL_ID} --profile default
fi

rm -rf .ask

if [[ -f skill-package/skill.json.backup ]]; then
  mv skill-package/skill.json.backup skill-package/skill.json
fi

### Finish off the undeployment
cd ..
