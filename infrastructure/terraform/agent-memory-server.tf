resource "tls_private_key" "agent_memory_server_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "agent_memory_server_keypair" {
  key_name   = "${var.application_prefix}-keypair-${random_id.random_id.hex}"
  public_key = tls_private_key.agent_memory_server_key.public_key_openssh

  tags = {
    Name = "agent-memory-server-keypair"
  }
}

resource "local_file" "agent_memory_server_private_key" {
  content         = tls_private_key.agent_memory_server_key.private_key_pem
  filename        = "${var.application_prefix}-private-key.pem"
  file_permission = "0400"
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }
}

# Security group for the agent memory server
resource "aws_security_group" "agent_memory_server" {
  name_prefix = "${var.application_prefix}-agent-memory-server"

  # API server
  ingress {
    from_port   = 8000
    to_port     = 8000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Uncommment to allow SSH access
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Outbound traffic
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.application_prefix}-agent-memory-server-sg"
  }
}

# Elastic IP for consistent address
resource "aws_eip" "agent_memory_server" {
  instance = aws_instance.agent_memory_server.id
  domain   = "vpc"

  tags = {
    Name = "agent-memory-server-eip"
  }
}

# EC2 Instance
resource "aws_instance" "agent_memory_server" {
  depends_on    = [rediscloud_essentials_database.redis_database]
  ami           = data.aws_ami.ubuntu.id
  instance_type = "c5.4xlarge"

  key_name               = aws_key_pair.agent_memory_server_keypair.key_name
  vpc_security_group_ids = [aws_security_group.agent_memory_server.id]

  root_block_device {
    volume_size = 50
    volume_type = "gp3"
  }

  user_data_base64 = base64encode(templatefile("templates/ec2-instance-setup.tftpl", {
    redis_database_url = "redis://default:${rediscloud_essentials_database.redis_database.password}@${rediscloud_essentials_database.redis_database.public_endpoint}"
    openai_api_key     = var.openai_api_key
  }))

  tags = {
    Name = "${var.application_prefix}-agent-memory-server"
  }
}

# Outputs
output "agent_memory_server_api_endpoint" {
  value = "http://${aws_eip.agent_memory_server.public_ip}:8000"
}

### Uncomment to get SSH command
output "ssh_command_to_agent_memory_server" {
  value = "ssh -i ${local_file.agent_memory_server_private_key.filename} ubuntu@${aws_eip.agent_memory_server.public_ip}"
}
