"""Run on a Pedidos360 EC2 through SSM; fetch configuration without logging secrets."""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

service = sys.argv[1]
directory = Path('/opt/pedidos360') / service
directory.mkdir(parents=True, exist_ok=True)
os.umask(0o077)

def run(*args, capture=False):
    return subprocess.run(args, check=True, text=True, capture_output=capture)

response = run('aws', 'ssm', 'get-parameter', '--region', 'us-east-1', '--name', '/pedidos360/lab/runtime',
               '--with-decryption', '--query', 'Parameter.Value', '--output', 'text', capture=True)
state = json.loads(response.stdout)
outputs = state['outputs']

def write_env(values):
    (directory / '.env').write_text(''.join(f'{key}={value}\n' for key, value in values.items()), encoding='utf-8')

if service == 'init-db':
    import oracledb
    connection = oracledb.connect(user='p360master', password=state['dbMasterPassword'],
                                 dsn=outputs['OracleEndpoint'] + ':1521/P360')
    with connection.cursor() as cursor:
        for suffix, password in state['schemaPasswords'].items():
            username = 'P360_' + suffix
            cursor.execute('SELECT COUNT(*) FROM dba_users WHERE username=:name', name=username)
            if cursor.fetchone()[0] == 0:
                cursor.execute(f'CREATE USER {username} IDENTIFIED BY "{password}"')
                cursor.execute(f'GRANT CREATE SESSION, CREATE TABLE TO {username}')
                cursor.execute(f'ALTER USER {username} QUOTA 100M ON USERS')
            print('Schema ready:', username, flush=True)
    connection.close()
    for suffix, password in state['schemaPasswords'].items():
        connection = oracledb.connect(user='P360_' + suffix, password=password,
                                     dsn=outputs['OracleEndpoint'] + ':1521/P360')
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM user_tables WHERE table_name='P360_DOCUMENTS'")
            if cursor.fetchone()[0] == 0:
                cursor.execute('CREATE TABLE p360_documents (id VARCHAR2(160 CHAR) PRIMARY KEY, '
                               'kind VARCHAR2(40 CHAR) NOT NULL, payload CLOB NOT NULL, revision NUMBER(19,0) NOT NULL)')
                cursor.execute('CREATE INDEX p360_kind_idx ON p360_documents(kind)')
        connection.close()
    print('Four application schemas initialized.', flush=True)
    sys.exit(0)
if service == 'mq':
    write_env({'RABBIT_USER': 'pedidos360', 'RABBIT_PASSWORD': state['rabbitPassword'],
               'RABBIT_COOKIE': state['rabbitCookie'], 'MANAGEMENT_BIND': outputs['Ec2MqPrivateIp']})
elif service == 'kafka':
    write_env({'KAFKA_HOST': outputs['Ec2KafkaPrivateIp']})
else:
    registry = state['accountId'] + '.dkr.ecr.us-east-1.amazonaws.com'
    values = {
        'IMAGE_REPOSITORY': registry + '/pedidos360', 'IMAGE_TAG': state['imageTag'],
        'SPRING_PROFILES_ACTIVE': 'aws', 'ENTRA_ISSUER_URI': 'https://login.microsoftonline.com/' + state['tenantId'] + '/v2.0',
        'ENTRA_AUDIENCE': state['apiClientId'], 'CORS_ORIGINS': 'http://localhost:5173',
        'RABBIT_ADDRESSES': outputs['Ec2MqPrivateIp'] + ':5672',
        'RABBIT_USER': 'pedidos360', 'RABBIT_PASSWORD': state['rabbitPassword'],
        'RABBIT_MANAGEMENT_URL': 'http://' + outputs['Ec2MqPrivateIp'] + ':15672',
        'KAFKA_BOOTSTRAP_SERVERS': outputs['Ec2KafkaPrivateIp'] + ':9092',
        'SMTP_HOST': 'mailpit', 'SMTP_PORT': 1025, 'SMTP_AUTH': 'false', 'SMTP_TLS': 'false',
        'NOTIFY_DIR': '/app/data/notify',
    }
    for name in ('Catalog', 'Orders', 'Audit', 'Report', 'RabbitAdmin', 'KafkaAdmin'):
        key = {'RabbitAdmin': 'RABBIT_ADMIN', 'KafkaAdmin': 'KAFKA_ADMIN'}.get(name, name.upper()) + '_URL'
        port = {'Catalog': 8082, 'Orders': 8083, 'Audit': 8084, 'Report': 8085, 'RabbitAdmin': 8087, 'KafkaAdmin': 8088}[name]
        docker_name={'RabbitAdmin':'rabbit-admin','KafkaAdmin':'kafka-admin'}.get(name,name.lower())
        values[key] = 'http://' + docker_name + ':' + str(port)
    values['DB_URL'] = 'jdbc:oracle:thin:@//' + outputs['OracleEndpoint'] + ':1521/P360'
    (directory / 'common.env').write_text(''.join(f'{key}={value}\n' for key,value in values.items()),encoding='utf-8')
    for suffix,password in state['schemaPasswords'].items():
        values[suffix+'_DB_PASSWORD']=password
    write_env(values)
    password = run('aws', 'ecr', 'get-login-password', '--region', 'us-east-1', capture=True).stdout
    subprocess.run(['docker', 'login', '--username', 'AWS', '--password-stdin', registry], input=password,
                   text=True, check=True)
    del password
os.chdir(directory)
run('docker', 'compose', '--env-file', '.env', 'config', '--quiet')
run('docker', 'compose', '--env-file', '.env', 'up', '-d', '--pull', 'always')
print('Compose started:', service, flush=True)
