# Descripcion del proyecto:

## 1. AWS Pipeline

## Technologies:
- AWS latest SDK
- Kinesis
- AWS Lambda
- CloudWatch
- DynamoDB
- PostgreSQL
- Redis
- Terraform
- LocalStack
- Scala latest version
- ZIO latest version 
- Slick latest version
- Scanamo latest version
- sttp3 latest version
- CIRCE latest version

## Functional Requirements:
Pipeline de procesamiento de datos que:
- AWS Lambda consume de un stream de entrada en lotes de 500 mensajes
- cada mensaje tiene la siguiente estructura 
````{node_id: String Mandatory, registration_id: String optional, baseline_id: String optional, value: Double mandatory}````
- Por cada mensaje realiza una llamada HTTP GET / localhost:3333/:registration_id, con registration_id tomado del mensaje
- Guaer 


## 1. Pipeline Management & Dasboard

## Technologies:
- NodeJS v22
- TypeScript
- React

## Functional Requirements:
Interface de usuario que permite controlar y visualizar los recursos de AWS pipeline
- Panel informativo de los distintos recursos e informacion de su estado
- Leer de los streams
- Escribir en los streams  
- Ver los mensajes de log de las aws lambda
- Simular una carga de trabajo generando menajes de entrada
- Visualizar la simulacion de la carga de trabajo  representando graficamente los distintos recursos