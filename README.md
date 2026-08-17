🏥 AuraCare Health Platform
A comprehensive healthcare queue management and patient tracking system built with Spring Boot and React.

https://img.shields.io/badge/Java-17-ED8B00?style=flat&logo=java&logoColor=white
https://img.shields.io/badge/Spring%2520Boot-3.2.4-6DB33F?style=flat&logo=springboot&logoColor=white
https://img.shields.io/badge/React-18.2.0-61DAFB?style=flat&logo=react&logoColor=white
https://img.shields.io/badge/PostgreSQL-15-4169E1?style=flat&logo=postgresql&logoColor=white
https://img.shields.io/badge/License-MIT-yellow.svg

📋 Table of Contents
Overview

Features

Tech Stack

Architecture

Getting Started

API Documentation

Deployment

Security

Contributing

License

📖 Overview
AuraCare is a modern healthcare management platform designed to streamline patient flow, queue management, and clinical operations. It provides real-time tracking for patients, doctors, and administrators across multiple facilities and departments.

Key Capabilities
Patient Journey: Check-in → Triage → Consultation → Lab Tests → Payment → Discharge

Real-time Updates: SSE (Server-Sent Events) for live queue status

Multi-Facility: Support for multiple healthcare facilities and departments

Insurance Integration: Automatic insurance claim calculations (RSSB, MUTUELLE, MMI)

Audit Logging: Complete audit trail for all system actions

✨ Features
👤 Patient Features
Feature	Description
Patient Landing	Choice between walk-in visit or appointment booking
Check-in	Self-service check-in with triage
Queue Status	Real-time queue position tracking via SSE
Medical History	View complete medical history
Billing	View and pay bills online
Appointments	Book and manage appointments
👨‍⚕️ Doctor Features
Feature	Description
Queue Management	View assigned patient queue
Consultation	Start/complete consultations
Lab Orders	Order and complete lab tests
Patient Records	Access patient medical history
🏢 Admin Features
Feature	Description
Multi-Facility Telemetry	Live dashboard for all facilities
User Management	Create, update, manage users
Department Management	Manage departments across facilities
Staff Management	Assign staff to facilities/departments
Financial Dashboard	Revenue, claims, and financial reports
Audit Logs	Complete system audit trail
Service Pricing	Configure service pricing and insurance coverage
Insurance Providers	Manage insurance providers
🛠️ Tech Stack
Backend
text
- Java 17
- Spring Boot 3.2.4
- Spring Security (JWT)
- Spring Data JPA (Hibernate)
- PostgreSQL 15
- Hibernate Envers (Audit Logging)
- Maven
- JWT (JSON Web Tokens)
  Frontend
  text
- React 18.2.0
- TypeScript
- Tailwind CSS
- Zustand (State Management)
- React Hook Form
- Zod (Validation)
- React Router v6
- Axios
- Server-Sent Events (SSE)
  DevOps & Monitoring
  text
- Docker (Optional)
- Railway / Render / Vercel (Deployment)
- UptimeRobot (Monitoring)
  🏗️ Architecture
  System Flow
  text
  ┌─────────────────────────────────────────────────────────────┐
  │                         USER                               │
  └─────────────────────────────────────────────────────────────┘
  │
  ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                     REACT FRONTEND                         │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
  │  │ Patient  │  │ Doctor   │  │ Admin    │  │ Staff    │  │
  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
  └─────────────────────────────────────────────────────────────┘
  │
  ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                   SPRING BOOT BACKEND                      │
  │  ┌────────────────────────────────────────────────────────┐│
  │  │  Controllers → Services → Repositories → Database     ││
  │  └────────────────────────────────────────────────────────┘│
  │  ┌────────────────────────────────────────────────────────┐│
  │  │  Security (JWT) │ SSE (Real-time) │ Audit Logging    ││
  │  └────────────────────────────────────────────────────────┘│
  └─────────────────────────────────────────────────────────────┘
  │
  ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                   POSTGRESQL DATABASE                      │
  └─────────────────────────────────────────────────────────────┘
  Core Modules
  text
  ┌─────────────────────────────────────────────────────────────┐
  │                     MODULES                                 │
  ├─────────────────────────────────────────────────────────────┤
  │  ├── Authentication & Authorization (JWT + MFA)            │
  │  ├── Queue Management (Priority-based)                     │
  │  ├── Consultation & Lab Workflow                           │
  │  ├── Billing & Insurance Processing                        │
  │  ├── Audit & Compliance Logging                            │
  │  ├── Multi-Facility Management                             │
  │  ├── Real-time Notifications (SSE)                         │
  │  └── Reporting & Analytics                                 │
  └─────────────────────────────────────────────────────────────┘