resource "aws_instance" "app" {
  ami                         = var.ec2_ami_id
  instance_type               = var.ec2_instance_type
  subnet_id                   = aws_subnet.public["a"].id
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  associate_public_ip_address = true
  user_data                   = templatefile("${path.module}/templates/user-data.sh.tftpl", {})
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "disabled"
  }

  root_block_device {
    volume_size           = var.root_volume_size_gib
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
    tags = {
      Name      = "${local.name_prefix}-app-root"
      Component = "compute"
    }
  }

  # Bootstrap needs working HTTPS egress and SSM permissions at first boot.
  depends_on = [
    aws_route.public_internet,
    aws_route_table_association.public,
    aws_vpc_security_group_egress_rule.app_https,
    aws_iam_role_policy_attachment.ssm
  ]

  tags = {
    Name      = "${local.name_prefix}-app"
    Component = "compute"
    Tier      = "application"
  }
}
