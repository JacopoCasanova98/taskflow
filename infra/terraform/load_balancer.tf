resource "aws_lb" "app" {
  name                       = "${local.name_prefix}-alb"
  internal                   = false
  load_balancer_type         = "application"
  ip_address_type            = "ipv4"
  subnets                    = [aws_subnet.public["a"].id, aws_subnet.public["b"].id]
  security_groups            = [aws_security_group.alb.id]
  xff_header_processing_mode = "append"
  drop_invalid_header_fields = true
  enable_deletion_protection = true
  tags                       = { Component = "compute", Tier = "edge" }
}

resource "aws_lb_target_group" "app" {
  name        = "${local.name_prefix}-app"
  target_type = "instance"
  protocol    = "HTTP"
  port        = 8080
  vpc_id      = aws_vpc.this.id

  health_check {
    protocol            = "HTTP"
    port                = "traffic-port"
    path                = "/internal/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  tags = { Component = "compute" }
}

resource "aws_lb_target_group_attachment" "app" {
  target_group_arn = aws_lb_target_group.app.arn
  target_id        = aws_instance.app.id
  port             = 8080
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = var.acm_certificate_arn
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09"
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
      message_body = "Not found"
    }
  }
}

resource "aws_lb_listener_rule" "internal_health" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 1
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
      message_body = "Not found"
    }
  }
  condition {
    path_pattern {
      values = ["/internal/*", "/actuator", "/actuator/*"]
    }
  }
}

# Separate rule: ALB permits at most three comparisons per path condition.
resource "aws_lb_listener_rule" "documentation" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 2
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
      message_body = "Not found"
    }
  }
  condition {
    path_pattern { values = ["/swagger-ui*", "/v3/api-docs*"] }
  }
}

resource "aws_lb_listener_rule" "public_host" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
  condition {
    host_header { values = [var.public_hostname] }
  }
}
