variable "application_prefix" {
  description = "Prefix for all resources"
  type        = string
}

variable "database_predefined" {
  description = "Whether to use a predefined Redis database"
  type        = bool
  default     = false
}

variable "subscription_name" {
  description = "Name of the Redis subscription"
  type        = string
}

variable "database_name" {
  description = "Name of the Redis database"
  type        = string
}

variable "payment_card_type" {
  description = "Type of the payment card"
  type        = string
}

variable "payment_card_last_four" {
  description = "Last four digits of the payment card"
  type        = string
}

variable "essentials_plan_cloud_provider" {
  description = "Cloud provider for the essentials plan"
  type        = string
}

variable "essentials_plan_cloud_region" {
  description = "Cloud region for the essentials plan"
  type        = string
}

variable "openai_api_key" {
  description = "OpenAI API key for memory server"
  type        = string
  sensitive   = true
}

variable "openai_embedding_model_name" {
  description = "OpenAI model name for the skill"
  type        = string
  default = "text-embedding-3-small"
}

variable "openai_model_name" {
  description = "OpenAI model name for the skill"
  type        = string
}

variable "openai_chat_temperature" {
  description = "Temperature setting for OpenAI chat model"
  type        = number
  default     = 0.8
}

variable "openai_chat_max_tokens" {
  description = "Maximum tokens for OpenAI chat model"
  type        = number
  default     = 4096
}

variable "alexa_skill_id" {
  type = string
}

variable "knowledge_base_bucket_name" {
  description = "S3 bucket for knowledge data files"
  type        = string
}

variable "create_knowledge_base_bucket" {
  description = "Whether to create a new knowledge base bucket or use an existing one"
  type        = bool
  default     = true
}

resource "random_id" "random_id" {
  byte_length = 4
}
