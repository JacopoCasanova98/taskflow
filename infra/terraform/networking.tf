resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name      = "${local.name_prefix}-vpc"
    Component = "networking"
  }
}

resource "aws_subnet" "public" {
  for_each = var.public_subnet_cidrs

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value
  availability_zone       = local.availability_zones[each.key]
  map_public_ip_on_launch = true

  tags = {
    Name      = "${local.name_prefix}-public-${each.key}"
    Component = "networking"
    Tier      = "public"
  }
}

resource "aws_subnet" "database" {
  for_each = var.database_subnet_cidrs

  vpc_id                  = aws_vpc.this.id
  cidr_block              = each.value
  availability_zone       = local.availability_zones[each.key]
  map_public_ip_on_launch = false

  tags = {
    Name      = "${local.name_prefix}-database-${each.key}"
    Component = "networking"
    Tier      = "database"
  }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name      = "${local.name_prefix}-igw"
    Component = "networking"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name      = "${local.name_prefix}-public-rt"
    Component = "networking"
    Tier      = "public"
  }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# Database isolation: only the implicit VPC local route, no Internet/NAT route.
resource "aws_route_table" "database" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name      = "${local.name_prefix}-database-rt"
    Component = "networking"
    Tier      = "database"
  }
}

resource "aws_route_table_association" "database" {
  for_each = aws_subnet.database

  subnet_id      = each.value.id
  route_table_id = aws_route_table.database.id
}
