resource "null_resource" "my_jarvis_alexa_skill_handler_build" {
  depends_on = [aws_instance.agent_memory_server]

  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    command     = "mvn clean package"
    interpreter = ["bash", "-c"]
    working_dir = "../lambda"
  }
}

data "local_file" "my_jarvis_skill_handler_jar_file" {
  depends_on = [null_resource.my_jarvis_alexa_skill_handler_build]
  filename   = "../lambda/target/my-jarvis-alexa-skill-1.0.jar"
}

resource "aws_s3_bucket" "my_jarvis_alexa_skill_handler_lambda_artifacts" {
  bucket        = "${var.application_prefix}-lambda-artifacts"
}

resource "aws_s3_object" "my_jarvis_skill_handler_lambda_jar" {
  bucket = aws_s3_bucket.my_jarvis_alexa_skill_handler_lambda_artifacts.id
  key    = "functions/${var.application_prefix}/1.0/function.jar"
  source = data.local_file.my_jarvis_skill_handler_jar_file.filename
  etag   = data.local_file.my_jarvis_skill_handler_jar_file.content_md5
}

data "aws_s3_bucket" "existing_knowledge_base" {
  count  = var.create_knowledge_base_bucket ? 0 : 1
  bucket = var.knowledge_base_bucket_name
}

resource "aws_s3_bucket" "my_jarvis_alexa_skill_handler_knowledge_base" {
  count         = var.create_knowledge_base_bucket ? 1 : 0
  bucket        = var.knowledge_base_bucket_name
}

locals {
  knowledge_base_bucket_id   = var.create_knowledge_base_bucket ? aws_s3_bucket.my_jarvis_alexa_skill_handler_knowledge_base[0].id : data.aws_s3_bucket.existing_knowledge_base[0].id
  knowledge_base_bucket_name = var.create_knowledge_base_bucket ? aws_s3_bucket.my_jarvis_alexa_skill_handler_knowledge_base[0].bucket : data.aws_s3_bucket.existing_knowledge_base[0].bucket
  knowledge_base_bucket_arn  = var.create_knowledge_base_bucket ? aws_s3_bucket.my_jarvis_alexa_skill_handler_knowledge_base[0].arn : data.aws_s3_bucket.existing_knowledge_base[0].arn
}

resource "aws_s3_object" "my_jarvis_alexa_skill_handler_knowledge_base_ingest_folder" {
  bucket = local.knowledge_base_bucket_id
  key    = "ingest/"
}

resource "aws_s3_object" "my_jarvis_alexa_skill_handler_knowledge_base_processed_folder" {
  bucket = local.knowledge_base_bucket_id
  key    = "processed/"
}

resource "aws_s3_object" "my_jarvis_alexa_skill_handler_knowledge_base_failed_folder" {
  bucket = local.knowledge_base_bucket_id
  key    = "failed/"
}

resource "aws_iam_role" "my_jarvis_alexa_skill_handler_role" {
  name               = "${var.application_prefix}-_role"
  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow"
    }
  ]
}
EOF
}

resource "aws_iam_role_policy" "my_jarvis_alexa_skill_handler_role_policy" {
  role = aws_iam_role.my_jarvis_alexa_skill_handler_role.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Resource = ["*"]
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "ec2:CreateNetworkInterface",
          "ec2:DescribeDhcpOptions",
          "ec2:DescribeNetworkInterfaces",
          "ec2:DeleteNetworkInterface",
          "ec2:DescribeSubnets",
          "ec2:DescribeSecurityGroups",
          "ec2:DescribeVpcs"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:ListBucket",
          "s3:DeleteObject",
          "s3:PutObject"
        ]
        Resource = [
          "arn:aws:s3:::${local.knowledge_base_bucket_name}/*",
          "arn:aws:s3:::${local.knowledge_base_bucket_name}"
        ]
      }
    ]
  })
}

resource "aws_lambda_function" "my_jarvis_alexa_skill_handler" {
  depends_on = [
    null_resource.my_jarvis_alexa_skill_handler_build,
    aws_iam_role.my_jarvis_alexa_skill_handler_role,
    aws_instance.agent_memory_server,
    aws_s3_object.my_jarvis_skill_handler_lambda_jar
  ]
  function_name    = "${var.application_prefix}-function"
  description      = "Backend function for the My Jarvis Alexa Skill"
  s3_bucket        = aws_s3_bucket.my_jarvis_alexa_skill_handler_lambda_artifacts.id
  s3_key           = aws_s3_object.my_jarvis_skill_handler_lambda_jar.key
  source_code_hash = data.local_file.my_jarvis_skill_handler_jar_file.content_base64sha256
  handler          = "io.redis.devrel.demos.myjarvis.MyJarvisStreamHandler::handleRequest"
  role             = aws_iam_role.my_jarvis_alexa_skill_handler_role.arn
  runtime          = "java21"
  memory_size      = 512
  timeout          = 60
  environment {
    variables = {
      OPENAI_API_KEY               = var.openai_api_key
      OPENAI_MODEL_NAME            = var.openai_model_name
      OPENAI_CHAT_TEMPERATURE      = var.openai_chat_temperature
      OPENAI_CHAT_MAX_TOKENS       = var.openai_chat_max_tokens
      COHERE_API_KEY               = var.cohere_api_key
      COHERE_MODEL_NAME            = var.cohere_model_name
      AGENT_MEMORY_SERVER_URL      = "http://${aws_eip.agent_memory_server.public_ip}:8000"
      KNOWLEDGE_BASE_BUCKET_NAME   = local.knowledge_base_bucket_name
    }
  }
}

resource "aws_lambda_permission" "my_jarvis_alexa_skill_handler_alexa_trigger" {
  statement_id       = "AllowExecutionFromAlexa"
  action             = "lambda:InvokeFunction"
  function_name      = aws_lambda_function.my_jarvis_alexa_skill_handler.function_name
  principal          = "alexa-appkit.amazon.com"
  event_source_token = var.alexa_skill_id != "" ? var.alexa_skill_id : null
}

resource "aws_lambda_permission" "my_jarvis_alexa_skill_handler_cloudwatch_trigger" {
  statement_id  = "AllowExecutionFromCloudWatch"
  action        = "lambda:InvokeFunction"
  principal     = "events.amazonaws.com"
  function_name = aws_lambda_function.my_jarvis_alexa_skill_handler.function_name
  source_arn    = aws_cloudwatch_event_rule.my_jarvis_alexa_skill_handler_knowledge_base.arn
}

resource "aws_cloudwatch_event_rule" "my_jarvis_alexa_skill_handler_knowledge_base" {
  name                = "${var.application_prefix}-knowledge-update"
  description         = "Update My Jarvis knowledge base continuously"
  schedule_expression = "rate(1 minute)"
}

resource "aws_cloudwatch_event_target" "my_jarvis_alexa_skill_handler_knowledge_base" {
  rule      = aws_cloudwatch_event_rule.my_jarvis_alexa_skill_handler_knowledge_base.name
  target_id = aws_lambda_function.my_jarvis_alexa_skill_handler.function_name
  arn       = aws_lambda_function.my_jarvis_alexa_skill_handler.arn
  input     = templatefile("templates/knowledge-base-call.tftpl", {})
}

output "my_jarvis_alexa_skill_handler_arn" {
  value = aws_lambda_function.my_jarvis_alexa_skill_handler.arn
}
