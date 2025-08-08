# Open-CSMS Requirements - Notes de travail

## Décisions prises

### Scope géographique
- **Europe** (avec conformité AFIR)

### Standards OCPP
- **OCPP 1.6 + 2.0.1** (rétrocompatibilité avec l'existant)
  - Permet adoption plus large dès le début
  - Compatible avec infrastructure existante
  - Prêt pour les nouvelles exigences AFIR

### Types de déploiements
- **Architecture flexible** : déployable sur cloud public, cloud privé, ou serveurs dédiés
- **Support multi-tenant** : permettre aux acteurs SaaS d'utiliser open-csms comme base business
- **Gestion multi-CPO** : un SaaS peut gérer plusieurs CPO clients
- **Déploiement autonome** : chaque entreprise peut déployer sa propre instance

### Modèle de données multi-tenant
- **Isolation logique** avec tenant_id dans chaque table
- Optimisation performance avec indexation appropriée
- Simplicité opérationnelle pour les SaaS providers
- Gestion des droits d'accès au niveau applicatif

### Fonctionnalités métier MVP (inspiré scope SteVe)
- **Gestion complète des charging stations - EXPERT GRADE**
  - Configuration avancée : tous paramètres OCPP 2.0.1
  - Smart charging profiles, display messages, certificats PKI
  - Paramètres constructeur, firmware OTA, diagnostics poussés
  - Gestion connecteurs multiples (Type2, CCS, CHAdeMO)
  - Logs détaillés, métriques hardware, contrôle total
- **Gestion complète des badges - COMPATIBILITÉ MAXIMALE**
  - RFID traditionnel (ISO14443, Mifare)
  - NFC smartphone (émulation badge via app mobile)
  - QR codes dynamiques (généré par app, sécurisé)
  - Plug & Charge ISO 15118 (authentification par câble/véhicule)
  - Autocharge (reconnaissance automatique véhicule)
  - API externe (intégration systèmes tiers)
  - Roaming (interopérabilité autres réseaux CPO)
  - Gestion groupes (entreprises, flottes, famille)
  - Tarification différenciée par type utilisateur
  - Restrictions temporelles/géographiques
- **Gestion complète des sessions de charge - TÉLÉMÉTRIE COMPLÈTE**
  - Monitoring temps réel : toutes métriques OCPP + métriques custom constructeur
  - Courbes de charge, température, diagnostics réseau détaillés
  - Fréquence mesures configurable (1s à 1min selon besoin)
  - Gestion interruptions : coupure réseau, panne station, reprise session
  - Facturation précise multi-critères (kWh, temps, puissance max, tarifs dynamiques)
  - Alertes temps réel (anomalies, seuils, prédictive)
  - Export données (billing, analytics, audit, conformité)
  - Historique long terme avec archivage intelligent
  - Start/stop, monitoring temps réel, historique

### Différenciation stratégique
- **Hub OCPI 2.2.1 complet** (Open Charge Point Interface)
  - Capacité hub : interconnecter d'autres CPO (business model SaaS puissant)
  - Support OCPI 2.2.1 avec toutes fonctionnalités avancées
  - Core modules : locations, tariffs, sessions, cdrs, tokens
  - Advanced features : smart charging coordination, reservations
  - Game changer pour éviter vendor lock-in et créer écosystème ouvert

### Architecture technique
- **Architecture modulaire hybride** (monolithe modulaire + services découplés)
  - Équilibre optimal : performance + simplicité déploiement
  - Core monolithe pour cohérence transactionnelle (sessions, billing)
  - Services découplés pour fonctions périphériques (analytics, notifications)
  - Évolutivité sans complexité excessive
  - Déploiement simple pour petits CPO, scalable pour SaaS

### Stack technologique
- **Java/Spring moderne** (ecosystem mature, expertise large)
  - Spring Boot 3.x avec WebFlux pour performance reactive
  - Compatibilité conceptuelle avec SteVe (migration facilitée)
  - Large pool de développeurs qualifiés
  - Écosystem mature pour OCPP/OCPI
  - Performance enterprise-grade
  - Déploiement Docker simplifié

### Base de données et stockage
- **Architecture polyglotte spécialisée**
  - **PostgreSQL** : données métier (CPO, users, stations, sessions)
  - **InfluxDB** : télémétrie temps réel (métriques, courbes charge)
  - **Redis** : cache haute performance + session WebSocket OCPP
  - **Elasticsearch** : logs, audit, recherche full-text (optionnel)
  - Optimisation par use case vs simplicité pure
  - Performance maximale pour chaque type de données

### Interfaces utilisateur et APIs
- **Web app moderne** pour interface admin/CPO (React/Vue SPA)
- **APIs robustes pour écosystème ouvert :**
  - REST OCPI 2.2.1 (hub interopérabilité)
  - GraphQL (flexibilité intégrations tierces)
  - WebSocket (temps réel dashboards)
- **Philosophie** : backend puissant + interfaces légères
- Focus sur APIs plutôt que interfaces propriétaires
- Permet aux SaaS de créer leurs propres UI

### Sécurité et authentification
- **Authentification flexible** : support multiple providers + self-hosted
  - OIDC/OAuth2, SAML, LDAP/AD, providers cloud (Auth0, Keycloak)
  - Self-hosted option pour souveraineté données
  - MFA configurable (obligatoire/optionnel selon déploiement)
- **Sécurité OCPP/OCPI enterprise-grade :**
  - TLS mutuel avec gestion certificats PKI
  - API Keys avec scopes granulaires par tenant
  - Rate limiting anti-DDoS configurable
  - Audit logging complet (GDPR/SOX ready)
- **Compliance européenne :**
  - GDPR by design (pseudonymisation, right to be forgotten)
  - ISO 27001 compatible (certifiable)
  - Cybersecurity Act EU ready

### Modèle économique et licensing
- **Apache 2.0 License** (usage commercial libre, adoption maximale)
  - Permet aux CPO d'utiliser sans contraintes
  - Encourage contributions entreprises sans crainte copyleft
  - Compatible avec écosystème commercial (SaaS, intégrateurs)
  - Favorise croissance rapide de l'écosystème
- **Business model** : écosystème ouvert + services
  - Support professionnel, consulting, certifications
  - Managed hosting pour petits CPO
  - Marketplace plugins et extensions
  - Formation et expertise

### Stratégie de déploiement et ops
- **Docker Compose simple** (1-click pour petits CPO)
  - Philosophie "boring technology" : zéro complexité ops
  - docker-compose.yml avec tous services préconfigurés
  - Configuration via variables d'environnement
  - Backup automatique intégré
  - Health checks et auto-restart
- **Monitoring simplifié mais complet :**
  - Dashboards intégrés (business + technique)
  - Alerting essentiel (email/webhook)
  - Métriques OCPP/OCPI en temps réel
  - Logs centralisés pour debug

## Finalisation requirements
- Requirements fonctionnels : ✅ complets
- Requirements techniques : ✅ complets  
- Requirements opérationnels : ✅ complets