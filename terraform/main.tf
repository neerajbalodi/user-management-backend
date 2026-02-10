provider "aws" {
  region = "us-east-1"
}

# Replace 'sg-xxxxxxxx' with your actual Security Group ID from AWS Console
variable "my_sg_id" {
  default = "sg-0de0865bac85c2241" 
}

# 1. Database
resource "aws_instance" "database" {
  ami                    = "ami-0b6c6ebed2801a5cb"
  instance_type          = "t2.micro"
  vpc_security_group_ids = [var.my_sg_id]
  key_name               = "user"
  tags                   = { Name = "App-Database" }
}

# 2. Backend
resource "aws_instance" "backend" {
  ami                    = "ami-0b6c6ebed2801a5cb"
  instance_type          = "t2.micro"
  vpc_security_group_ids = [var.my_sg_id]
  key_name               = "user"
  tags                   = { Name = "App-Backend" }
}

# 3. Frontend
resource "aws_instance" "frontend" {
  ami                    = "ami-0b6c6ebed2801a5cb"
  instance_type          = "t2.micro"
  vpc_security_group_ids = [var.my_sg_id]
  key_name               = "user"
  tags                   = { Name = "App-Frontend" }
}

output "frontend_ip" { value = aws_instance.frontend.public_ip }
output "backend_ip"  { value = aws_instance.backend.public_ip }
output "db_ip"       { value = aws_instance.database.private_ip }
