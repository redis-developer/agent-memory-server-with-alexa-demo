locals {
  redis_database_url = var.database_predefined ? "redis://default:${data.rediscloud_essentials_database.redis_database[0].password}@${data.rediscloud_essentials_database.redis_database[0].public_endpoint}" : "redis://default:${rediscloud_essentials_database.redis_database[0].password}@${rediscloud_essentials_database.redis_database[0].public_endpoint}"
  name_base          = substr(var.application_prefix, 0, 16)
}

data "aws_region" "current" {}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "aws_security_group" "agent_memory_server_alb" {
  name_prefix = "${var.application_prefix}-ams-alb-"
  description = "ALB security group for Agent Memory Server"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 8000
    to_port     = 8000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.application_prefix}-agent-memory-server-alb-sg"
  }
}

resource "aws_security_group" "agent_memory_server_tasks" {
  name_prefix = "${var.application_prefix}-ams-tasks-"
  description = "ECS tasks security group for Agent Memory Server"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port       = 8000
    to_port         = 8000
    protocol        = "tcp"
    security_groups = [aws_security_group.agent_memory_server_alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.application_prefix}-agent-memory-server-tasks-sg"
  }
}

resource "aws_lb" "agent_memory_server" {
  name               = "${local.name_base}-ams-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.agent_memory_server_alb.id]
  subnets            = data.aws_subnets.default.ids

  tags = {
    Name = "${var.application_prefix}-agent-memory-server-alb"
  }
}

resource "aws_lb_target_group" "agent_memory_server" {
  name        = "${local.name_base}-ams-tg"
  port        = 8000
  protocol    = "HTTP"
  vpc_id      = data.aws_vpc.default.id
  target_type = "ip"

  health_check {
    path                = "/v1/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${var.application_prefix}-agent-memory-server-tg"
  }
}

resource "aws_lb_listener" "agent_memory_server" {
  load_balancer_arn = aws_lb.agent_memory_server.arn
  port              = 8000
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.agent_memory_server.arn
  }
}

resource "aws_ecs_cluster" "agent_memory_server" {
  name = "${var.application_prefix}-agent-memory-server"
}

resource "aws_iam_role" "agent_memory_server_task_execution" {
  name_prefix = "${var.application_prefix}-ams-ecs-exec-"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = { Service = "ecs-tasks.amazonaws.com" },
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "agent_memory_server_task_execution" {
  role       = aws_iam_role.agent_memory_server_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_cloudwatch_log_group" "agent_memory_server_api" {
  name              = "/ecs/${var.application_prefix}/agent-memory-server-api"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "agent_memory_server_worker" {
  name              = "/ecs/${var.application_prefix}/agent-memory-server-worker"
  retention_in_days = 7
}

resource "aws_ecs_task_definition" "agent_memory_server_api" {
  family                   = "${var.application_prefix}-agent-memory-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.agent_memory_server_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "memory-api"
      image     = var.agent_memory_server_image
      essential = true
      command   = ["agent-memory", "api", "--host", "0.0.0.0", "--port", "8000"]
      portMappings = [
        {
          containerPort = 8000
          hostPort      = 8000
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "DISABLE_AUTH", value = "true" },
        { name = "REDIS_URL", value = local.redis_database_url },
        { name = "OPENAI_API_KEY", value = var.openai_api_key },
        { name = "LONG_TERM_MEMORY", value = "true" },
        { name = "ENABLE_DISCRETE_MEMORY_EXTRACTION", value = "true" },
        { name = "GENERATION_MODEL", value = "gpt-4o-mini" },
        { name = "HOST", value = "0.0.0.0" },
        { name = "PORT", value = "8000" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.agent_memory_server_api.name
          awslogs-region        = data.aws_region.current.id
          awslogs-stream-prefix = "api"
        }
      }
    },
  ])
}

resource "aws_ecs_task_definition" "agent_memory_server_worker" {
  family                   = "${var.application_prefix}-agent-memory-worker"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.agent_memory_server_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "memory-worker"
      image     = var.agent_memory_server_image
      essential = true
      command   = ["agent-memory", "task-worker", "--concurrency", "10"]
      environment = [
        { name = "DISABLE_AUTH", value = "true" },
        { name = "REDIS_URL", value = local.redis_database_url },
        { name = "OPENAI_API_KEY", value = var.openai_api_key },
        { name = "LONG_TERM_MEMORY", value = "true" },
        { name = "ENABLE_DISCRETE_MEMORY_EXTRACTION", value = "true" },
        { name = "GENERATION_MODEL", value = "gpt-4o-mini" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.agent_memory_server_worker.name
          awslogs-region        = data.aws_region.current.id
          awslogs-stream-prefix = "worker"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "agent_memory_server_api" {
  name            = "${var.application_prefix}-agent-memory-api"
  cluster         = aws_ecs_cluster.agent_memory_server.id
  task_definition = aws_ecs_task_definition.agent_memory_server_api.arn
  desired_count   = var.api_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.agent_memory_server_tasks.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.agent_memory_server.arn
    container_name   = "memory-api"
    container_port   = 8000
  }

  depends_on = [aws_lb_listener.agent_memory_server]
}

resource "aws_ecs_service" "agent_memory_server_worker" {
  name            = "${var.application_prefix}-agent-memory-worker"
  cluster         = aws_ecs_cluster.agent_memory_server.id
  task_definition = aws_ecs_task_definition.agent_memory_server_worker.arn
  desired_count   = var.worker_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.default.ids
    security_groups  = [aws_security_group.agent_memory_server_tasks.id]
    assign_public_ip = true
  }
}

resource "aws_appautoscaling_target" "agent_memory_server_api" {
  max_capacity       = var.api_max_capacity
  min_capacity       = var.api_min_capacity
  resource_id        = "service/${aws_ecs_cluster.agent_memory_server.name}/${aws_ecs_service.agent_memory_server_api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_target" "agent_memory_server_worker" {
  max_capacity       = var.worker_max_capacity
  min_capacity       = var.worker_min_capacity
  resource_id        = "service/${aws_ecs_cluster.agent_memory_server.name}/${aws_ecs_service.agent_memory_server_worker.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "agent_memory_server_api_cpu" {
  name               = "${var.application_prefix}-ams-api-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.agent_memory_server_api.resource_id
  scalable_dimension = aws_appautoscaling_target.agent_memory_server_api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.agent_memory_server_api.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.api_cpu_target
    scale_in_cooldown  = var.api_scale_in_cooldown
    scale_out_cooldown = var.api_scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_appautoscaling_policy" "agent_memory_server_api_memory" {
  name               = "${var.application_prefix}-ams-api-memory"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.agent_memory_server_api.resource_id
  scalable_dimension = aws_appautoscaling_target.agent_memory_server_api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.agent_memory_server_api.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.api_memory_target
    scale_in_cooldown  = var.api_scale_in_cooldown
    scale_out_cooldown = var.api_scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
  }
}

resource "aws_appautoscaling_policy" "agent_memory_server_api_alb_requests" {
  count              = var.enable_alb_request_scaling ? 1 : 0
  name               = "${var.application_prefix}-ams-api-alb-requests"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.agent_memory_server_api.resource_id
  scalable_dimension = aws_appautoscaling_target.agent_memory_server_api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.agent_memory_server_api.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.api_alb_requests_per_target
    scale_in_cooldown  = var.api_scale_in_cooldown
    scale_out_cooldown = var.api_scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.agent_memory_server.arn_suffix}/${aws_lb_target_group.agent_memory_server.arn_suffix}"
    }
  }
}

resource "aws_appautoscaling_policy" "agent_memory_server_worker_cpu" {
  name               = "${var.application_prefix}-ams-worker-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.agent_memory_server_worker.resource_id
  scalable_dimension = aws_appautoscaling_target.agent_memory_server_worker.scalable_dimension
  service_namespace  = aws_appautoscaling_target.agent_memory_server_worker.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.worker_cpu_target
    scale_in_cooldown  = var.worker_scale_in_cooldown
    scale_out_cooldown = var.worker_scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_appautoscaling_policy" "agent_memory_server_worker_memory" {
  name               = "${var.application_prefix}-ams-worker-memory"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.agent_memory_server_worker.resource_id
  scalable_dimension = aws_appautoscaling_target.agent_memory_server_worker.scalable_dimension
  service_namespace  = aws_appautoscaling_target.agent_memory_server_worker.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = var.worker_memory_target
    scale_in_cooldown  = var.worker_scale_in_cooldown
    scale_out_cooldown = var.worker_scale_out_cooldown

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageMemoryUtilization"
    }
  }
}

output "agent_memory_server_api_endpoint" {
  value = "http://${aws_lb.agent_memory_server.dns_name}:8000"
}
