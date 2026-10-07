"""Create initial Compose and CloudFormation artifacts; review before any deployment."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
def write(path,data):
    p=root/path;p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(data,indent=2)+'\n' if isinstance(data,dict) else data,encoding='utf-8')

mq={'services':{},'volumes':{'rabbit1':{},'rabbit2':{}}}
for i in (1,2):
    mq['services'][f'rabbit{i}']={'image':'rabbitmq:4.1-management','hostname':f'rabbit{i}','restart':'unless-stopped','environment':{'RABBITMQ_NODENAME':f'rabbit@rabbit{i}','RABBITMQ_ERLANG_COOKIE':'${RABBIT_COOKIE:?set RABBIT_COOKIE}','RABBITMQ_DEFAULT_USER':'${RABBIT_USER:-pedidos360}','RABBITMQ_DEFAULT_PASS':'${RABBIT_PASSWORD:?set RABBIT_PASSWORD}'},'volumes':[f'rabbit{i}:/var/lib/rabbitmq','./rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro'],'healthcheck':{'test':['CMD','rabbitmqctl','eval','case length(rabbit_nodes:list_running()) of N when N >= 2 -> ok; _ -> erlang:error(cluster_not_ready) end.'],'interval':'10s','timeout':'10s','retries':12}}
    mq['services'][f'rabbit{i}']['ports']=[f'{5672+i-1}:5672',f'${{MANAGEMENT_BIND:-127.0.0.1}}:{15672+i-1}:15672']
write('infra/mq/compose.yml',mq)

kafka={'services':{},'volumes':{}}
for i in (1,2,3):
    kafka['volumes'][f'zk{i}-data']={};kafka['volumes'][f'zk{i}-log']={};kafka['volumes'][f'kafka{i}']={}
    kafka['services'][f'zk{i}']={'image':'confluentinc/cp-zookeeper:7.7.1','hostname':f'zk{i}','restart':'unless-stopped','environment':{'ZOOKEEPER_SERVER_ID':str(i),'ZOOKEEPER_CLIENT_PORT':'2181','ZOOKEEPER_TICK_TIME':'2000','ZOOKEEPER_INIT_LIMIT':'10','ZOOKEEPER_SYNC_LIMIT':'5','ZOOKEEPER_SERVERS':'zk1:2888:3888;zk2:2888:3888;zk3:2888:3888'},'volumes':[f'zk{i}-data:/var/lib/zookeeper/data',f'zk{i}-log:/var/lib/zookeeper/log']}
    port=9091+i
    kafka['services'][f'kafka{i}']={'image':'confluentinc/cp-kafka:7.7.1','hostname':f'kafka{i}','restart':'unless-stopped','depends_on':['zk1','zk2','zk3'],'ports':[f'{port}:{port}'],'environment':{'KAFKA_BROKER_ID':str(i),'KAFKA_ZOOKEEPER_CONNECT':'zk1:2181,zk2:2181,zk3:2181','KAFKA_LISTENERS':f'INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:{port}','KAFKA_ADVERTISED_LISTENERS':f'INTERNAL://kafka{i}:29092,EXTERNAL://${{KAFKA_HOST:-localhost}}:{port}','KAFKA_LISTENER_SECURITY_PROTOCOL_MAP':'INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT','KAFKA_INTER_BROKER_LISTENER_NAME':'INTERNAL','KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR':'3','KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR':'3','KAFKA_TRANSACTION_STATE_LOG_MIN_ISR':'2','KAFKA_DEFAULT_REPLICATION_FACTOR':'3','KAFKA_MIN_INSYNC_REPLICAS':'2','KAFKA_AUTO_CREATE_TOPICS_ENABLE':'false','KAFKA_HEAP_OPTS':'-Xms256m -Xmx512m'},'volumes':[f'kafka{i}:/var/lib/kafka/data'],'healthcheck':{'test':['CMD','kafka-topics','--bootstrap-server','localhost:29092','--list'],'interval':'15s','timeout':'10s','retries':20}}
topics=[('orders.events','delete',604800000),('audit.timeline','compact,delete',2592000000),('orders.events.ms-pedidos360-report.DLT','delete',1209600000),('audit.timeline.ms-pedidos360-audit.DLT','delete',1209600000)]
commands=['set -e']
for topic,policy,retention in topics:
    commands.append(f'kafka-topics --bootstrap-server kafka1:29092 --create --if-not-exists --topic {topic} --partitions 3 --replication-factor 3 --config cleanup.policy={policy} --config retention.ms={retention} --config min.insync.replicas=2')
kafka['services']['init-topics']={'image':'confluentinc/cp-kafka:7.7.1','depends_on':{f'kafka{i}':{'condition':'service_healthy'} for i in (1,2,3)},'entrypoint':['/bin/bash','-c'],'command':['\n'.join(commands)],'restart':'no'}
kafka['services']['kafka-ui']={'image':'provectuslabs/kafka-ui:v0.7.2','restart':'unless-stopped','ports':['127.0.0.1:8090:8080'],'environment':{'KAFKA_CLUSTERS_0_NAME':'pedidos360','KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS':'kafka1:29092,kafka2:29092,kafka3:29092','KAFKA_CLUSTERS_0_ZOOKEEPER':'zk1:2181,zk2:2181,zk3:2181'},'depends_on':['kafka1','kafka2','kafka3']}
write('infra/kafka/compose.yml',kafka)

services=[('bff',8080),('catalog',8082),('orders',8083),('audit',8084),('report',8085),('notify',8086),('rabbit-admin',8087),('kafka-admin',8088)]
local={'services':{},'volumes':{}}
for name,port in services:
    module='ms-pedidos360-'+name
    environment={'ENTRA_ISSUER_URI':'${ENTRA_ISSUER_URI:?set issuer}','ENTRA_AUDIENCE':'${ENTRA_AUDIENCE:?set audience}','RABBIT_ADDRESSES':'host.docker.internal:5672,host.docker.internal:5673','RABBIT_USER':'${RABBIT_USER:-pedidos360}','RABBIT_PASSWORD':'${RABBIT_PASSWORD:?set password}','KAFKA_BOOTSTRAP_SERVERS':'host.docker.internal:9092,host.docker.internal:9093,host.docker.internal:9094','CATALOG_URL':'http://catalog:8082','ORDERS_URL':'http://orders:8083','AUDIT_URL':'http://audit:8084','REPORT_URL':'http://report:8085','RABBIT_ADMIN_URL':'http://rabbit-admin:8087','KAFKA_ADMIN_URL':'http://kafka-admin:8088','RABBIT_MANAGEMENT_URL':'http://host.docker.internal:15672','SMTP_HOST':'mailpit','CORS_ORIGINS':'${CORS_ORIGINS:-http://localhost:5173}'}
    local['services'][name]={'build':{'context':'../..','dockerfile':'backend/Dockerfile','args':{'SERVICE':module}},'image':f'pedidos360/{name}:1.0.0','restart':'unless-stopped','environment':environment,'ports':[f'127.0.0.1:{port}:{port}'],'extra_hosts':['host.docker.internal:host-gateway'],'volumes':[f'{name}-data:/app/data']}
    local['volumes'][f'{name}-data']={}
    aws={'services':{name:{'image':f'${{IMAGE_REPOSITORY:?set repository}}/{name}:${{IMAGE_TAG:-1.0.0}}','restart':'unless-stopped','env_file':['.env'],'ports':[f'{port}:{port}'],'volumes':[f'{name}-data:/app/data'],'logging':{'driver':'json-file','options':{'max-size':'10m','max-file':'3'}}}},'volumes':{f'{name}-data':{}}}
    write(f'infra/apps/{name}/compose.yml',aws)
local['services']['mailpit']={'image':'axllent/mailpit:v1.24','ports':['127.0.0.1:8025:8025']}
import copy
for name,service in mq['services'].items():
    local['services'][name]=copy.deepcopy(service)
    local['services'][name]['volumes']=[v.replace('./rabbitmq.conf','../mq/rabbitmq.conf') for v in service['volumes']]
local['services'].update(copy.deepcopy(kafka['services']))
local['volumes'].update(mq['volumes']);local['volumes'].update(kafka['volumes'])
for name,_ in services:
    env=local['services'][name]['environment'];env['RABBIT_ADDRESSES']='rabbit1:5672,rabbit2:5672';env['RABBIT_MANAGEMENT_URL']='http://rabbit1:15672';env['KAFKA_BOOTSTRAP_SERVERS']='kafka1:29092,kafka2:29092,kafka3:29092'
    local['services'][name]['depends_on']={'rabbit1':{'condition':'service_healthy'},'rabbit2':{'condition':'service_healthy'},'init-topics':{'condition':'service_completed_successfully'}}
write('infra/local/compose.yml',local)

# Existing VPC/subnets are supplied by the operator. No AWS operation is performed.
ref=lambda x:{'Ref':x}
att=lambda r,a:{'Fn::GetAtt':[r,a]}
sub=lambda s:{'Fn::Sub':s}
t={'AWSTemplateFormatVersion':'2010-09-09','Description':'Pedidos360: eight EC2 services, RabbitMQ/Kafka hosts, RDS Oracle, private ALB and JWT HTTP API. Creates billable resources.','Parameters':{'VpcId':{'Type':'AWS::EC2::VPC::Id'},'PrivateSubnets':{'Type':'List<AWS::EC2::Subnet::Id>','Description':'At least two private subnets in different AZs, with outbound access via NAT or endpoints'},'AmiId':{'Type':'AWS::SSM::Parameter::Value<AWS::EC2::Image::Id>','Default':'/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64'},'AppInstanceType':{'Type':'String','Default':'t3.small'},'BrokerInstanceType':{'Type':'String','Default':'t3.large'},'DbInstanceClass':{'Type':'String','Default':'db.t3.medium'},'EntraIssuer':{'Type':'String','AllowedPattern':'https://.+'},'EntraAudience':{'Type':'String','Description':'Exact aud claim of the API access token'},'FrontendOrigin':{'Type':'String','Default':'http://localhost:5173'}},'Resources':{},'Outputs':{}}
r=t['Resources']
for name in ('AppSG','BrokerSG','DatabaseSG','AlbSG','LinkSG'):
    r[name]={'Type':'AWS::EC2::SecurityGroup','Properties':{'GroupDescription':'Pedidos360 '+name,'VpcId':ref('VpcId')}}
for name,target,source,port,end in [('AppInternal','AppSG','AppSG',8080,8088),('AlbToBff','AppSG','AlbSG',8080,8080),('LinkToAlb','AlbSG','LinkSG',80,80),('OracleFromApps','DatabaseSG','AppSG',1521,1521),('AmqpFromApps','BrokerSG','AppSG',5672,5673),('RabbitManagementFromApps','BrokerSG','AppSG',15672,15673),('KafkaFromApps','BrokerSG','AppSG',9092,9094)]:
    r[name]={'Type':'AWS::EC2::SecurityGroupIngress','Properties':{'GroupId':ref(target),'SourceSecurityGroupId':ref(source),'IpProtocol':'tcp','FromPort':port,'ToPort':end}}
r['Ec2Role']={'Type':'AWS::IAM::Role','Properties':{'AssumeRolePolicyDocument':{'Version':'2012-10-17','Statement':[{'Effect':'Allow','Principal':{'Service':'ec2.amazonaws.com'},'Action':'sts:AssumeRole'}]},'ManagedPolicyArns':[sub('arn:${AWS::Partition}:iam::aws:policy/AmazonSSMManagedInstanceCore'),sub('arn:${AWS::Partition}:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly')]}}
r['Ec2Profile']={'Type':'AWS::IAM::InstanceProfile','Properties':{'Roles':[ref('Ec2Role')]}}
userdata='''#!/bin/bash
set -eu
dnf install -y docker
systemctl enable --now docker
mkdir -p /usr/local/lib/docker/cli-plugins /opt/pedidos360
curl -fsSL https://github.com/docker/compose/releases/download/v2.39.4/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
'''
for name,_ in services+[('mq',0),('kafka',0)]:
    logical='Ec2'+''.join(p.title() for p in name.split('-'))
    r[logical]={'Type':'AWS::EC2::Instance','Properties':{'ImageId':ref('AmiId'),'InstanceType':ref('BrokerInstanceType' if name in ('mq','kafka') else 'AppInstanceType'),'SubnetId':{'Fn::Select':[0,ref('PrivateSubnets')]},'SecurityGroupIds':[ref('BrokerSG' if name in ('mq','kafka') else 'AppSG')],'IamInstanceProfile':ref('Ec2Profile'),'MetadataOptions':{'HttpTokens':'required'},'BlockDeviceMappings':[{'DeviceName':'/dev/xvda','Ebs':{'VolumeSize':40,'VolumeType':'gp3','Encrypted':True}}],'UserData':{'Fn::Base64':userdata},'Tags':[{'Key':'Name','Value':'pedidos360-'+name}]}}
    t['Outputs'][logical+'PrivateIp']={'Value':att(logical,'PrivateIp')}
r['DbSubnetGroup']={'Type':'AWS::RDS::DBSubnetGroup','Properties':{'DBSubnetGroupDescription':'Pedidos360 private Oracle subnets','SubnetIds':ref('PrivateSubnets')}}
r['Oracle']={'Type':'AWS::RDS::DBInstance','DeletionPolicy':'Snapshot','UpdateReplacePolicy':'Snapshot','Properties':{'Engine':'oracle-se2','LicenseModel':'license-included','DBInstanceClass':ref('DbInstanceClass'),'AllocatedStorage':'20','MaxAllocatedStorage':100,'StorageType':'gp3','StorageEncrypted':True,'DBName':'P360','MasterUsername':'p360master','ManageMasterUserPassword':True,'DBSubnetGroupName':ref('DbSubnetGroup'),'VPCSecurityGroups':[ref('DatabaseSG')],'PubliclyAccessible':False,'BackupRetentionPeriod':7,'DeletionProtection':True,'CopyTagsToSnapshot':True}}
r['Alb']={'Type':'AWS::ElasticLoadBalancingV2::LoadBalancer','Properties':{'Scheme':'internal','Type':'application','Subnets':ref('PrivateSubnets'),'SecurityGroups':[ref('AlbSG')]}}
r['TargetGroup']={'Type':'AWS::ElasticLoadBalancingV2::TargetGroup','Properties':{'VpcId':ref('VpcId'),'Port':8080,'Protocol':'HTTP','TargetType':'instance','HealthCheckPath':'/actuator/health','Targets':[{'Id':ref('Ec2Bff'),'Port':8080}]}}
r['Listener']={'Type':'AWS::ElasticLoadBalancingV2::Listener','Properties':{'LoadBalancerArn':ref('Alb'),'Port':80,'Protocol':'HTTP','DefaultActions':[{'Type':'forward','TargetGroupArn':ref('TargetGroup')}]}}
r['HttpApi']={'Type':'AWS::ApiGatewayV2::Api','Properties':{'Name':'pedidos360','ProtocolType':'HTTP','CorsConfiguration':{'AllowOrigins':[ref('FrontendOrigin')],'AllowHeaders':['Authorization','Content-Type','X-Correlation-Id'],'AllowMethods':['GET','POST','PUT','DELETE','OPTIONS']}}}
r['JwtAuthorizer']={'Type':'AWS::ApiGatewayV2::Authorizer','Properties':{'ApiId':ref('HttpApi'),'Name':'entra-jwt','AuthorizerType':'JWT','IdentitySource':['$request.header.Authorization'],'JwtConfiguration':{'Issuer':ref('EntraIssuer'),'Audience':[ref('EntraAudience')]}}}
r['VpcLink']={'Type':'AWS::ApiGatewayV2::VpcLink','Properties':{'Name':'pedidos360','SubnetIds':ref('PrivateSubnets'),'SecurityGroupIds':[ref('LinkSG')]}}
r['Integration']={'Type':'AWS::ApiGatewayV2::Integration','Properties':{'ApiId':ref('HttpApi'),'IntegrationType':'HTTP_PROXY','IntegrationMethod':'ANY','IntegrationUri':ref('Listener'),'ConnectionType':'VPC_LINK','ConnectionId':ref('VpcLink'),'PayloadFormatVersion':'1.0','RequestParameters':{'overwrite:path':'$request.path'}}}
for logical,path in [('Root','ANY /api'),('Api','ANY /api/{proxy+}')]:
    r['Route'+logical]={'Type':'AWS::ApiGatewayV2::Route','Properties':{'ApiId':ref('HttpApi'),'RouteKey':path,'AuthorizationType':'JWT','AuthorizationScopes':['access_as_user'],'AuthorizerId':ref('JwtAuthorizer'),'Target':sub('integrations/${Integration}')}}
r['Stage']={'Type':'AWS::ApiGatewayV2::Stage','Properties':{'ApiId':ref('HttpApi'),'StageName':'$default','AutoDeploy':True,'DefaultRouteSettings':{'ThrottlingBurstLimit':100,'ThrottlingRateLimit':50}}}
t['Outputs']['ApiUrl']={'Value':att('HttpApi','ApiEndpoint')}
t['Outputs']['OracleEndpoint']={'Value':att('Oracle','Endpoint.Address')}
t['Outputs']['MasterSecretArn']={'Value':att('Oracle','MasterUserSecret.SecretArn')}
write('infra/aws/cloudformation.json',t)
