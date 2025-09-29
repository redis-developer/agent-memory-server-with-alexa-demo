# Adding memory capabilities to Amazon Alexa with Redis Agent Memory Server

Amazon Alexa is arguably one of the most popular virtual assistant devices available in many homes worldwide. It enables users to automate mundane tasks, such as setting timers, playing music, and making phone calls. It would be great if only one thing weren't true—it doesn't really remember anything. Whatever conversations you have with Alexa, it won't be used in future conversations as context for more elaborate, polished, and well-tailored answers. Therefore, repeating yourself with Alexa is a common occurrence. This repository changes everything by providing you with a memory-enabled skill for Amazon Alexa, capable of reusing previous memories and providing a more contextual conversation with users, allowing them to use Alexa in a more impactful manner.

![my-jarvis-interaction.png](images/my-jarvis-interaction.png)

This is implemented as an [Alexa skill](https://developer.amazon.com/en-US/alexa/alexa-skills-kit) using [Java](https://www.java.com/en). The behavior of the skill is deployed as an [AWS Lambda](https://aws.amazon.com/lambda) function, which in turn manages memories using the [Redis Agent Memory Server](https://redis.github.io/agent-memory-server). The build and deployment of the Alexa skill is fully automated using Bash scripts, Terraform, and the ASK CLI.

## 🧑🏻‍💻 Account requirements
- [AWS account](https://aws.amazon.com/account) with permissions to create Lambda and IAM resources
- [Amazon developer account](https://developer.amazon.com) to create new Alexa skills from scratch
- [Redis Cloud account](https://redis.io/try-free) to create a Redis database for the memory server

## 📋 Software requirements
- Java 21+: https://www.oracle.com/java/technologies/downloads/
- Maven 3.9+: https://maven.apache.org/install.html
- Terraform: https://developer.hashicorp.com/terraform/install
- ASK CLI: https://github.com/alexa/ask-cli
- JQ: https://jqlang.org/
- SED: https://formulae.brew.sh/formula/gnu-sed

## ‍💻 Preparing for deployment

This repository provides scripts that automate the build, deployment, and undeployment of the Alexa skill, along with its required resources. Everything is fully automated, but it requires that you have your accounts configured correctly in the machine you will use for deployment.

### Preparing your AWS account

This Alexa skill requires some backend resources to be created so it can function correctly. These resources will primarily be hosted on AWS, including Lambda functions, EC2 instances, and other services. You need to configure your AWS account locally before executing the deployment script.

1. Install: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html
2. Configure credentials: `aws configure` (must have access to deploy Lambda, IAM, etc.)

### Preparing your Amazon developer account

During deployment, a new skill called `My Jarvis` will be created for you. However, you need to ensure that your credentials are all set up with your ASK CLI. Follow these steps to configure your credentials.

1. Install: https://developer.amazon.com/en-US/docs/alexa/smapi/ask-cli-command-reference.html#install
2. Configure: `ask configure` (must be linked to your Amazon Developer account)

### Preparing your Redis Cloud account

This Alexa skill uses the Redis Agent Memory Server to provide memory capabilities. The memory server requires a Redis database to store and retrieve data. You must use Redis Cloud for this purpose. Follow these steps to make your Redis Cloud account accessible for Terraform.

1. Obtain your Redis Cloud API access and secret key from your Redis Cloud account.
2. Export them as environment variables:
 ```sh
export REDISCLOUD_ACCESS_KEY=<THIS_IS_GOING_TO_BE_YOUR_API_ACCOUNT_KEY>
export REDISCLOUD_SECRET_KEY=<THIS_IS_GOING_TO_BE_ONE_API_USER_KEY>
 ```

### Terraform configuration

During deployment, resources will be created by Terraform based on the variables you provide. You need to create a variables file with the correct information so the deployment can happen successfully.

1. Create a Terraform variables file by copying the example provided:
```sh
cp infrastructure/terraform/terraform.tfvars.example infrastructure/terraform/terraform.tfvars
```
2. Edit `infrastructure/terraform/terraform.tfvars` to set the following variables:
- `payment_card_type`: The credit card associated with your Redis Cloud account (e.g., "Visa").
- `payment_card_last_four`: The last four digits of the credit card associated with your Redis Cloud account (e.g., "1234").
- `essentials_plan_cloud_provider`: The cloud provider where you want your Redis database to be hosted (e.g., "AWS").
- `essentials_plan_cloud_region`: The region where you want your Redis database to be hosted (e.g., "us-east-1").
- `openai_api_key`: The OpenAI API key used by the Alexa skill to produce answers and the Agent Memory Server to manage memories.

## ⚙️ Installation & Deployment

Once all prerequisites and configuration are in place, installation is a single step:

```sh
./deploy.sh
```

## 🪓 Teardown

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
