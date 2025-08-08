# Open-CSMS

**Open-Source Charging Station Management System**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2+-green.svg)](https://spring.io/projects/spring-boot)
[![OCPP](https://img.shields.io/badge/OCPP-1.6%20|%202.0.1-blue.svg)](https://www.openchargealliance.org/)

## Overview

Open-CSMS is a comprehensive, enterprise-grade Charging Station Management System designed to be open-source, scalable, and production-ready. It supports multiple OCPP versions, multi-tenant architecture, and provides a complete solution for managing electric vehicle charging infrastructure.

## 🚀 Features

### Core Functionality
- **Multi-Tenant Architecture**: Complete tenant isolation for enterprise deployments
- **OCPP Protocol Support**: Full OCPP 1.6 and 2.0.1 implementation
- **Station Management**: Comprehensive charging station lifecycle management
- **User Management**: Complete user system with RBAC
- **Authentication**: Multi-provider authentication (password, RFID, NFC, API keys, OIDC)
- **Real-time Communication**: WebSocket-based OCPP messaging
- **REST API**: Complete RESTful API for all operations

### Technical Features
- **Spring Boot 3.x**: Modern Java framework
- **Multi-module Architecture**: Clean separation of concerns
- **Database Support**: PostgreSQL with multi-tenant isolation
- **Security**: JWT authentication, RBAC, password policies
- **Monitoring**: Comprehensive statistics and health checks
- **Docker Support**: Production-ready containerization

## 📋 Requirements

- **Java**: 17 or higher
- **Database**: PostgreSQL 12+
- **Redis**: For caching and session management
- **Docker**: For development and deployment
- **Gradle**: 8.0+ (wrapper included)

## 🏁 Quick Start

### Development Environment

1. **Clone the repository**:
   ```bash
   git clone https://github.com/katak95/open-csms.git
   cd open-csms
   ```

2. **Start dependencies with Docker Compose**:
   ```bash
   docker-compose -f docker/dev-compose.yml up -d
   ```

3. **Run the application**:
   ```bash
   ./gradlew bootRun
   ```

4. **Access the application**:
   - API: `http://localhost:8080/api/v1`
   - Documentation: `http://localhost:8080/swagger-ui.html`
   - Database Admin: `http://localhost:5050` (pgAdmin)

### Production Deployment

```bash
docker-compose -f docker/docker-compose.yml up -d
```

## 🏗️ Architecture

Open-CSMS follows a multi-module architecture:

```
open-csms/
├── modules/
│   ├── core/          # Core utilities and tenant management
│   ├── domain/        # Domain entities and repositories
│   ├── service/       # Business logic services
│   ├── security/      # Authentication and authorization
│   ├── ocpp/          # OCPP protocol implementation
│   ├── ocpi/          # OCPI protocol (future)
│   ├── web/           # REST controllers and DTOs
│   └── integration/   # External integrations
├── docker/            # Docker configurations
├── config/            # Configuration files
└── sql/              # Database migrations
```

## 📚 API Documentation

### Authentication
- `POST /api/v1/auth/login` - Username/password authentication
- `POST /api/v1/auth/rfid` - RFID token authentication
- `POST /api/v1/auth/oidc/init` - Initialize OIDC authentication

### Station Management
- `GET /api/v1/stations` - List charging stations
- `POST /api/v1/stations` - Create charging station
- `GET /api/v1/stations/{id}` - Get station details
- `PUT /api/v1/stations/{id}` - Update station

### User Management
- `GET /api/v1/users` - List users
- `POST /api/v1/users` - Create user
- `GET /api/v1/users/me` - Get current user profile

## 🔌 OCPP Support

### OCPP 1.6
- ✅ BootNotification
- ✅ Heartbeat
- ✅ StatusNotification
- 🚧 StartTransaction (Sprint 6)
- 🚧 StopTransaction (Sprint 6)
- 🚧 Authorize (Sprint 6)
- 🚧 MeterValues (Sprint 6)

### OCPP 2.0.1
- ✅ BootNotification
- ✅ Heartbeat
- ✅ StatusNotification
- 🚧 TransactionEvent (Sprint 6)

## 🛡️ Security Features

- **Multi-tenant Isolation**: Complete data separation between tenants
- **JWT Authentication**: Stateless authentication with refresh tokens
- **RBAC**: Role-based access control with granular permissions
- **Password Policies**: Configurable password strength requirements
- **Account Security**: Account lockout, failed attempt tracking
- **OIDC Integration**: Google, Microsoft, and other providers

## 📊 Development Progress

**Current Status**: Sprint 5 Completed ✅

- ✅ **Sprint 1**: Foundation & Infrastructure
- ✅ **Sprint 2**: Database & Multi-Tenant
- ✅ **Sprint 3**: Station CRUD & OCPP Foundation
- ✅ **Sprint 4**: OCPP Core Messages
- ✅ **Sprint 5**: User & Authentication
- 🚀 **Sprint 6**: Charging Sessions Core (In Progress)

See [DEVELOPMENT-PLAN.md](DEVELOPMENT-PLAN.md) for detailed roadmap.

## 🤝 Contributing

We welcome contributions! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting pull requests.

### Development Workflow
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Documentation**: See `/docs` directory
- **Issues**: [GitHub Issues](https://github.com/katak95/open-csms/issues)
- **Discussions**: [GitHub Discussions](https://github.com/katak95/open-csms/discussions)

## 🙏 Acknowledgments

- [Open Charge Alliance](https://www.openchargealliance.org/) for OCPP specifications
- [Spring Framework](https://spring.io/) team for the excellent framework
- All contributors and early adopters

---

**Open-CSMS** - Building the future of open-source charging infrastructure management.