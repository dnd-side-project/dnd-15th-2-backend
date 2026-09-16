# 프론트 테스트 서버 전용 VPC. NAT Gateway가 예산(월 10 USD)의 4배에 달해
# 프라이빗 서브넷을 두지 않는다(Infrastructure Design Report D-3 §4). 인스턴스는
# 퍼블릭 서브넷에 두고 Elastic IP로 아웃바운드·인바운드를 처리한다.
#
resource "aws_vpc" "this" {
  # CloudWatch Logs 수집 비용이 월 10 USD 예산을 잠식해 VPC Flow Logs를
  # 비활성화한다. dev 테스트 서버이고 저장 데이터가 테스트 데이터로 한정되어
  # 위험을 수용했다(Infrastructure Design Report D-3 §5 S-7). 프로덕션 전환
  # 또는 #132 MVP 아키텍처 설계 시 재검토한다.
  # checkov:skip=CKV2_AWS_11:dev 테스트 서버 예산 제약으로 Flow Logs를 비활성화한다. D-3 §5 S-7 참고.
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-vpc"
  })
}

# VPC 생성 시 AWS가 자동으로 만드는 기본 보안 그룹은 기본값이 전체 트래픽을
# 허용한다. 이 그룹을 직접 쓰지 않더라도 규칙을 비워 우발적 사용 시 노출을
# 막는다.
resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-default-sg-restricted"
  })
}

# map_public_ip_on_launch을 켜지 않는다. 인스턴스의 공인 IP는 별도 Elastic IP
# 연결로만 부여한다(checkov CKV_AWS_130 회피 목적이 아니라, 이 설계가 EIP
# 고정을 요구하기 때문에 자동 할당이 애초에 불필요하다).
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = false

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-public"
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-igw"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, {
    Name = "${var.project_prefix}-${var.name}-public-rt"
  })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}
