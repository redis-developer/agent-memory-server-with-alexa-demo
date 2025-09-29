# Adding memory capabilities to Amazon Alexa with Redis Agent Memory Server

Amazon Alexa is arguably one of the most popular virtual assistant devices available in many homes worldwide. It enables users to automate mundane tasks, such as setting timers, playing music, and making phone calls. It would be great if only one thing weren't true—it doesn't really remember anything. Whatever conversations you have with Alexa, it won't be used in future conversations as context for more elaborate, polished, and well-tailored answers. Therefore, repeating yourself with Alexa is a common occurrence. This repository changes everything by providing you with a memory-enabled skill for Amazon Alexa, capable of reusing previous memories and providing a more contextual conversation with users, allowing them to use Alexa in a more impactful manner.

![my-jarvis-interaction.png](images/my-jarvis-interaction.png)

This is implemented as an [Alexa skill](https://developer.amazon.com/en-US/alexa/alexa-skills-kit) using [Java](https://www.java.com/en). The behavior of the skill is deployed as an [AWS Lambda](https://aws.amazon.com/lambda) function, which in turn manages memories using the [Redis Agent Memory Server](https://redis.github.io/agent-memory-server). The build and deployment of the Alexa skill is fully automated using Bash scripts, Terraform, and the ASK CLI.

## 🧩 Account requirements
- [AWS account](https://aws.amazon.com/account) with permissions to create Lambda and IAM resources
- [Amazon developer account](https://developer.amazon.com) to create new Alexa skills from scratch
- [Redis Cloud account](https://redis.io/try-free) to create a Redis database for the memory server

## 📋 Software requirements
- Terraform: https://developer.hashicorp.com/terraform/install
- ASK CLI: https://github.com/alexa/ask-cli
- JQ: https://jqlang.org/
- SED: https://formulae.brew.sh/formula/gnu-sed

## Prerequisites (Required Before Installation)

**You must have the following installed and configured _before_ running the deployment script:**

1. **AWS CLI**
   - Install: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html
   - Configure credentials: `aws configure` (must have access to deploy Lambda, IAM, etc.)

2. **ASK CLI (Alexa Skills Kit CLI)**
   - Install: https://developer.amazon.com/en-US/docs/alexa/smapi/ask-cli-command-reference.html#install
   - Configure: `ask configure` (must be linked to your Amazon Developer account)

3. **Redis Cloud API Credentials**
   - Obtain your Redis Cloud API key and secret from your Redis Cloud account.
   - Export them as environment variables:
     ```sh
     export REDISCLOUD_ACCESS_KEY=your_account_api_key
     export REDISCLOUD_SECRET_KEY=your_user_api_key
     ```

4. **Terraform Variables File**
   - Copy the example file and edit it:
     ```sh
     cp infrastructure/terraform/terraform.tfvars.example infrastructure/terraform/terraform.tfvars
     # Edit terraform.tfvars and set the appropriate values (AWS region, resource names, etc.)
     ```

5. **Java 21+ and Maven 3.9++**
   - Required for building the Lambda function.

## Installation & Deployment

Once all prerequisites and configuration are in place, installation is a single step:

```sh
./deploy.sh
```

- This script will build the Lambda, deploy infrastructure with Terraform, upload the Lambda code, and update the Alexa skill using ASK CLI.
- Follow any prompts or error messages for troubleshooting.

## Teardown

To remove all deployed resources and the skill:

```sh
./undeploy.sh
```

## Project Structure
```
my-jarvis-alexa-skill/
├── lambda/                  # Java source code for the Alexa skill (AWS Lambda)
│   ├── pom.xml              # Maven build file
│   └── src/                 # Java source and resources
├── infrastructure/          # Terraform scripts for AWS infrastructure
│   └── terraform/
├── skill-package/           # Alexa skill manifest and assets
├── interactionModels/       # Alexa interaction models (intents, slots, etc.)
├── deploy.sh                # Deployment script
├── undeploy.sh              # Teardown script
└── README.md                # This file
```

## Usage
Invoke your Alexa device with the invocation name you set (e.g., "Alexa, open My Jarvis"). Try commands like:
- "Tell my javis to remember that my favorite color is blue."
- "Ask my jarvis to recall What is my favorite color?"
- "Tell my jarvis to Forget my favorite color."

## Contributing
Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Support
For questions or support, please open an issue in this repository.
