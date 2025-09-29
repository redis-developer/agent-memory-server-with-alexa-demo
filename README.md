# Adding memory capabilities to Amazon Alexa with Redis Agent Memory Server

Amazon Alexa is arguably one of the most popular virtual assistant devices available in many homes worldwide. It enables users to automate mundane tasks, such as setting timers, playing music, and making phone calls. It would be great if only one thing weren't true: it doesn't really remember anything. Whatever you speak with Alexa, it won't be used in future conversations as context for more elaborate, polished, and well-tailored answers. Repeating yourself with Alexa is a common occurrence. This repository changes the status quo by introducing a memory-enabled skill for Alexa, capable of reusing previous memories and providing a more contextual conversation with users, allowing them to use Alexa in a more impactful manner.

## Features
- Conversational Alexa skill with context retention
- Memory recall and forget functionality
- Integration with a Redis-backed working memory
- Knowledge base querying via external API
- Infrastructure as code using Terraform for AWS resources

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
