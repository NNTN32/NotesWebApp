"""Run over SSH as root on nntn-vps. Secrets/backups stay in root-only storage.

This script reconciles Docker Swarm and Dokploy metadata together. No HTTP
credentials are created for Dokploy. Run phases separately, checking output.
"""
import copy
import hashlib
import http.client
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time

BASE = Path('/var/lib/notesweb-hardening/20261005')
BASE.mkdir(parents=True, exist_ok=True, mode=0o700)
os.chmod(BASE, 0o700)
os.umask(0o077)
NAMES = {
    'be': 'nnnoteswebapp-notesserver-oubfhi',
    'pg': 'nnnoteswebapp-notespostgres-rbrlzd',
    'redis': 'nnnoteswebapp-notesredis-ztd7pp',
    'kafka': 'nnnoteswebapp-noteskafka-nkxwqp',
    'rabbit': 'nnnoteswebapp-notesrabbitmq-ceptee',
}

def run(args, data=None, check=True):
    p = subprocess.run(args, input=data, capture_output=True, text=True)
    if check and p.returncode:
        raise RuntimeError(f'Command failed ({args[0]}, exit {p.returncode}); sensitive output suppressed')
    return p

def cid(name):
    ids = run(['docker', 'ps', '-q', '--filter', f'label=com.docker.swarm.service.name={name}']).stdout.split()
    if len(ids) != 1:
        raise RuntimeError('Expected one running container for ' + name)
    return ids[0]

def sql(query, database='dokploy', container=None, user='dokploy'):
    return run(['docker', 'exec', '-i', container or cid('dokploy-postgres'),
                'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database, '-At'], query).stdout

def literal(value):
    return "'" + str(value).replace("'", "''") + "'"

class Docker(http.client.HTTPConnection):
    def __init__(self):
        super().__init__('localhost', timeout=120)
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.connect('/var/run/docker.sock')

def api(method, path, body=None):
    c = Docker()
    c.request(method, '/v1.47' + path, json.dumps(body) if body is not None else None,
              {'Content-Type': 'application/json'})
    r = c.getresponse()
    data = r.read()
    c.close()
    if r.status >= 300:
        raise RuntimeError(f'Docker API failed: {r.status} {path}')
    return json.loads(data) if data else None

def inspect(key):
    return api('GET', '/services/' + NAMES.get(key, key))

def update(key, mutate):
    current = inspect(key)
    spec = copy.deepcopy(current['Spec'])
    mutate(spec)
    api('POST', f"/services/{current['ID']}/update?version={current['Version']['Index']}", spec)

def env_set(spec, changes):
    values = dict(x.split('=', 1) for x in spec['TaskTemplate']['ContainerSpec'].get('Env', []))
    values.update(changes)
    spec['TaskTemplate']['ContainerSpec']['Env'] = [f'{k}={v}' for k,v in values.items()]

def wait(key):
    for _ in range(90):
        tasks = api('GET', '/tasks?filters=' + __import__('urllib.parse', fromlist=['quote']).quote(json.dumps({'service': [NAMES[key]], 'desired-state':['running']})))
        current = inspect(key)
        desired = current['Spec']['TaskTemplate']['ContainerSpec']
        running = [t for t in tasks if t['Status']['State'] == 'running' and
                   t['Spec']['ContainerSpec'].get('Image') == desired.get('Image') and
                   t['Spec']['ContainerSpec'].get('Env') == desired.get('Env') and
                   t['Spec'].get('ForceUpdate',0) == current['Spec']['TaskTemplate'].get('ForceUpdate',0) and
                   t['Spec']['ContainerSpec'].get('Args') == desired.get('Args') and
                   t['Spec']['ContainerSpec'].get('Mounts') == desired.get('Mounts')]
        if len(running) == 1:
            return running[0]['Status']['ContainerStatus']['ContainerID']
        time.sleep(2)
    raise RuntimeError('Service did not converge: ' + key)

def creds():
    path = BASE / 'credentials.json'
    if not path.exists():
        path.write_text(json.dumps({name: secrets.token_hex(24) for name in
            ['pg_observer', 'redis_app', 'redis_observer', 'redis_admin', 'rabbit_app', 'rabbit_monitor', 'rabbit_admin']}))
    return json.loads(path.read_text())

def metadata_update(table, name, changes):
    assignments = []
    for k,v in changes.items():
        if isinstance(v, (dict,list)):
            v = json.dumps(v)
        assignments.append('"' + k + '"=' + ('NULL' if v is None else literal(v)))
    sql('UPDATE "' + table + '" SET ' + ','.join(assignments) + ' WHERE "appName"=' + literal(name) + ';')

def metadata_env(name, changes):
    raw = sql('SELECT row_to_json(a) FROM application a WHERE "appName"=' + literal(name) + ';')
    row = json.loads(raw.strip())
    lines = (row.get('env') or '').splitlines()
    keys = set(changes)
    lines = [l for l in lines if l.partition('=')[0].strip() not in keys]
    lines += [f'{k}={v}' for k,v in changes.items()]
    metadata_update('application', name, {'env': '\n'.join(lines)})

def prepare():
    if (BASE / 'before.json').exists():
        print('Preparation already saved; preserving original backup')
        return
    snapshots = {key: inspect(key)['Spec'] for key in NAMES}
    (BASE / 'before.json').write_text(json.dumps(snapshots))
    for table in ['application', 'redis', 'postgres', 'mount']:
        raw = sql('SELECT COALESCE(json_agg(row_to_json(t)),\'[]\'::json) FROM "' + table + '" t;')
        (BASE / ('dokploy-' + table + '.json')).write_text(raw)
    c = creds()
    pg = cid(NAMES['pg'])
    query = """DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='notes_observer') THEN
CREATE ROLE notes_observer LOGIN; END IF; END $$;
ALTER ROLE notes_observer NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %s;
GRANT CONNECT ON DATABASE "NoteApp" TO notes_observer;
GRANT USAGE ON SCHEMA public TO notes_observer;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO notes_observer;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT ON TABLES TO notes_observer;
ALTER ROLE notes_observer SET default_transaction_read_only=on;
""" % literal(c['pg_observer'])
    sql(query, 'NoteApp', pg, 'postgres')
    result = sql("SELECT has_table_privilege('notes_observer','public.notes','SELECT'), has_table_privilege('notes_observer','public.notes','INSERT'), has_table_privilege('notes_observer','public.notes','UPDATE'), has_table_privilege('notes_observer','public.notes','DELETE');", 'NoteApp', pg, 'postgres')
    if result.strip() != 't|f|f|f':
        raise RuntimeError('Postgres observer privileges failed')
    print('Prepared root-only rollback snapshots; PostgreSQL observer SELECT=yes INSERT/UPDATE/DELETE=no')

def install_mount(key, volume, target):
    application_id = sql('SELECT "applicationId" FROM application WHERE "appName"=' + literal(NAMES[key]) + ';').strip()
    if not application_id:
        raise RuntimeError('Missing Dokploy application')
    exists = sql('SELECT count(*) FROM mount WHERE "applicationId"=' + literal(application_id) + ' AND "mountPath"=' + literal(target) + ';').strip()
    if exists == '0':
        sql('INSERT INTO mount ("mountId",type,"volumeName","serviceType","mountPath","applicationId") VALUES (' +
            ','.join(map(literal, [secrets.token_hex(12), 'volume', volume, 'application', target, application_id])) + ');')
    metadata_update('application', NAMES[key], {'updateConfigSwarm': {'Parallelism':1,'Order':'stop-first','FailureAction':'rollback'}, 'placementSwarm': {'Constraints':['node.hostname==nntn-vps']}})
    def change(s):
        cs = s['TaskTemplate']['ContainerSpec']
        mounts = cs.setdefault('Mounts', [])
        if not any(x['Target'] == target for x in mounts):
            mounts.append({'Type':'volume','Source':volume,'Target':target})
        s['UpdateConfig'] = {'Parallelism':1,'Order':'stop-first','FailureAction':'rollback'}
        s['TaskTemplate']['Placement'] = {'Constraints':['node.hostname==nntn-vps']}
    update(key, change)

def snapshot(key, path, volume, paused=False):
    container = cid(NAMES[key])
    run(['docker','volume','create',volume])
    mountpoint = run(['docker','volume','inspect',volume,'--format','{{.Mountpoint}}']).stdout.strip()
    if paused:
        run(['docker','pause',container])
    try:
        run(['docker','cp',container + ':' + path + '/.', mountpoint])
        backup = BASE / (key + '-data.tar')
        run(['tar','-cpf',str(backup),'-C',mountpoint,'.'])
        sha = hashlib.file_digest(backup.open('rb'), 'sha256').hexdigest()
        (BASE / (key + '-data.sha256')).write_text(sha + '  ' + backup.name + '\n')
    finally:
        if paused:
            run(['docker','unpause',container])
    print(key + ': saved data archive + SHA256 and seeded persistent volume')

def brokers():
    if (BASE / 'brokers-done').exists():
        print('Broker migration already completed')
        return
    update('be', lambda s: s['Mode']['Replicated'].update({'Replicas':0}))
    for _ in range(30):
        if not run(['docker','ps','-q','--filter','label=com.docker.swarm.service.name=' + NAMES['be']]).stdout.strip():
            break
        time.sleep(2)
    q = cid(NAMES['rabbit'])
    node = run(['docker','exec',q,'rabbitmqctl','eval','node().']).stdout.strip()
    # Atom output has no quotes for this node name; preserve it for disk restore.
    node = node.strip("'\n ")
    (BASE / 'rabbit-node').write_text(node)
    run(['docker','exec',q,'rabbitmqctl','stop_app'])
    snapshot('rabbit', '/var/lib/rabbitmq', 'notesweb-rabbitmq-data')
    snapshot('kafka', '/tmp/kafka-logs', 'notesweb-kafka-data', paused=True)
    metadata_env(NAMES['rabbit'], {'RABBITMQ_NODENAME':node})
    update('rabbit', lambda s: env_set(s, {'RABBITMQ_NODENAME':node}))
    install_mount('rabbit','notesweb-rabbitmq-data','/var/lib/rabbitmq')
    install_mount('kafka','notesweb-kafka-data','/tmp/kafka-logs')
    q = wait('rabbit')
    k = wait('kafka')
    for _ in range(60):
        if run(['docker','exec',q,'rabbitmq-diagnostics','-q','ping'],check=False).returncode == 0 and run(['docker','exec',k,'/opt/kafka/bin/kafka-topics.sh','--bootstrap-server','localhost:9093','--list'],check=False).returncode == 0:
            break
        time.sleep(2)
    else:
        raise RuntimeError('Broker readiness failed; BE remains stopped to protect data')
    update('be', lambda s: s['Mode']['Replicated'].update({'Replicas':1}))
    wait('be')
    (BASE / 'brokers-done').touch()
    print('Broker volumes active; existing Rabbit node and Kafka cluster preserved; BE restarted')

def fix_rabbit_hostname():
    node = (BASE / 'rabbit-node').read_text().strip()
    host = node.split('@',1)[1]
    wrapper = f'printf "\\n127.0.0.1 {host}\\n" >> /etc/hosts; rabbitmq-plugins enable --offline rabbitmq_stomp; exec docker-entrypoint.sh rabbitmq-server'
    import shlex
    metadata_update('application', NAMES['rabbit'], {'command':shlex.join(['sh','-c',wrapper])})
    def change(s):
        s['TaskTemplate']['ContainerSpec']['Args'] = ['sh','-c',wrapper]
        s['TaskTemplate']['ContainerSpec']['Hostname'] = host
    update('rabbit', change)
    wait('rabbit')
    print('Rabbit original node hostname pinned; Dokploy startup wrapper persists resolution and STOMP plugin')

def resume():
    for key,command in [('rabbit',['rabbitmq-diagnostics','-q','ping','--timeout','5']), ('kafka',['/opt/kafka/bin/kafka-topics.sh','--bootstrap-server','localhost:9093','--list'])]:
        for _ in range(30):
            container = cid(NAMES[key])
            if run(['docker','exec',container,*command],check=False).returncode == 0:
                break
            time.sleep(2)
        else:
            raise RuntimeError('Not ready: ' + key)
    update('be', lambda s: s['Mode']['Replicated'].update({'Replicas':1}))
    wait('be')
    (BASE / 'brokers-done').touch()
    print('Both brokers ready from persistent volumes; BE restored to 1 replica')

def fix_kafka_volume():
    mountpoint = run(['docker','volume','inspect','notesweb-kafka-data','--format','{{.Mountpoint}}']).stdout.strip()
    run(['chown','-R','1000:1000',mountpoint])
    update('be', lambda s: s['Mode']['Replicated'].update({'Replicas':0}))
    install_mount('kafka','notesweb-kafka-data','/tmp/kafka-logs')
    time.sleep(5)
    resume()

def address(key, port):
    container = cid(NAMES[key])
    values = api('GET','/containers/' + container + '/json')['NetworkSettings']['Networks']
    for item in values.values():
        for ip in [item.get('IPAddress'),item.get('Gateway')]:
            if not ip or ip == item.get('Gateway'):
                continue
            try:
                with socket.create_connection((ip,port),timeout=1):
                    return ip
            except OSError:
                pass
    # Overlay task also has a gateway bridge address not in the inspect map.
    for ip in run(['docker','exec',container,'hostname','-I']).stdout.split():
        try:
            with socket.create_connection((ip,port),timeout=1):
                return ip
        except OSError:
            pass
    raise RuntimeError('No reachable private address for ' + key)

def redis_command(*args, username=None, password=None):
    with socket.create_connection((address('redis',6379),6379),timeout=5) as conn:
        file = conn.makefile('rb')
        def request(parts):
            encoded = [str(x).encode() for x in parts]
            conn.sendall(b'*' + str(len(parts)).encode() + b'\r\n' + b''.join(b'$' + str(len(x)).encode() + b'\r\n' + x + b'\r\n' for x in encoded))
            def read():
                line = file.readline().rstrip(b'\r\n')
                if line[:1] in [b'+',b'-',b':']:
                    return line.decode()
                if line[:1] == b'$':
                    n = int(line[1:])
                    return None if n < 0 else file.read(n + 2)[:-2].decode()
                if line[:1] == b'*':
                    return [read() for _ in range(int(line[1:]))]
                raise RuntimeError('Redis protocol error')
            return read()
        if username:
            if request(['AUTH', username,password]) != '+OK':
                raise RuntimeError('Redis authentication failed')
        return request(args)

def access():
    c = creds()
    rid = cid(NAMES['redis'])
    hashed = lambda key: '#' + hashlib.sha256(c[key].encode()).hexdigest()
    app_rules = ['reset','on',hashed('redis_app'), '~auth:*','~notes:*','~media:*','~note:*','~session:*','~grace:*','~login:*',
        '+@connection','+@string','+@hash','+@stream','+@keyspace','-@admin','-@dangerous','+keys']
    observer_rules = ['reset','on',hashed('redis_observer'),'~*','+@connection','-@admin','+info','+scan','+type','+ttl','+pttl','+dbsize','+xinfo','+xpending',
        '(~notes:* ~note:* ~media:* +@read)']
    for user,rules in [('notes_app',app_rules),('notes_observer',observer_rules),('notes_admin',['reset','on',hashed('redis_admin'),'~*','&*','+@all'])]:
        result = redis_command('ACL','SETUSER',user,*rules)
        if result != '+OK':
            raise RuntimeError('Failed to provision Redis ACL user ' + user)
    volume = run(['docker','volume','inspect','nnnoteswebapp-notesredis-ztd7pp-data','--format','{{.Mountpoint}}']).stdout.strip()
    acl = Path(volume) / 'notesweb-users.acl'
    # Compatibility window until BE has authenticated successfully with its own account.
    # aclfile is immutable at runtime; save current ACLs then load file on restart.
    acl.write_text('\n'.join(redis_command('ACL','LIST')) + '\n')
    config = Path(volume) / 'notesweb-redis.conf'
    config.write_text('bind 0.0.0.0\nprotected-mode yes\ndir /data\naclfile /data/notesweb-users.acl\nappendonly yes\nappendfsync everysec\nsave 3600 1 300 100 60 10000\n')
    os.chown(acl,999,999)
    os.chown(config,999,999)
    metadata_update('redis',NAMES['redis'],{'command':'redis-server /data/notesweb-redis.conf','updateConfigSwarm':{'Parallelism':1,'Order':'stop-first','FailureAction':'pause'}})
    qid = cid(NAMES['rabbit'])
    users = run(['docker','exec',qid,'rabbitmqctl','list_users','--silent']).stdout
    for user,key,tag,permissions in [
        ('notes_app','rabbit_app','', ['.*','.*','.*']),
        ('notes_monitor','rabbit_monitor','monitoring',['','','']),
        ('notes_admin','rabbit_admin','administrator',['.*','.*','.*'])]:
        if user not in users:
            run(['docker','exec',qid,'rabbitmqctl','add_user',user,c[key]])
        run(['docker','exec',qid,'rabbitmqctl','set_user_tags',user,*([tag] if tag else [])])
        run(['docker','exec',qid,'rabbitmqctl','set_permissions','-p','/',user,*permissions])
    changes = {'SPRING_DATA_REDIS_USERNAME':'notes_app','SPRING_DATA_REDIS_PASSWORD':c['redis_app'],
               'RABBIT_USERNAME':'notes_app','RABBIT_PASSWORD':c['rabbit_app']}
    metadata_env(NAMES['be'],changes)
    update('be',lambda s: env_set(s,changes))
    # Wait for actual new task, not the previous task still shutting down.
    bid = wait('be')
    time.sleep(25)
    connected = redis_command('CLIENT','LIST',username='notes_admin',password=c['redis_admin'])
    if connected.count('user=notes_app') < 3:
        raise RuntimeError('BE has not authenticated Redis workers; default remains unchanged')
    connections = run(['docker','exec',qid,'rabbitmqctl','list_connections','user','--silent']).stdout
    if 'notes_app' not in connections:
        raise RuntimeError('BE Rabbit connection not ready; guest remains available')
    if redis_command('ACL','SETUSER','default','reset','off',username='notes_admin',password=c['redis_admin']) != '+OK':
        raise RuntimeError('Could not disable default Redis user')
    acl.write_text('\n'.join(redis_command('ACL','LIST',username='notes_admin',password=c['redis_admin'])) + '\n')
    os.chown(acl,999,999)
    if 'guest' in users:
        run(['docker','exec',qid,'rabbitmqctl','delete_user','guest'])
    metadata_env(NAMES['rabbit'],{'RABBITMQ_DEFAULT_USER':'notes_admin','RABBITMQ_DEFAULT_PASS':c['rabbit_admin']})
    # Redis AOF enabled live before its next restart, which also loads the config file.
    if redis_command('CONFIG','SET','appendonly','yes',username='notes_admin',password=c['redis_admin']) != '+OK':
        raise RuntimeError('Could not enable Redis AOF')
    def redis_config(s):
        s['TaskTemplate']['ContainerSpec']['Args'] = ['redis-server','/data/notesweb-redis.conf']
        s['UpdateConfig'] = {'Parallelism':1,'Order':'stop-first','FailureAction':'pause'}
    update('redis',redis_config)
    wait('redis')
    time.sleep(5)
    if redis_command('PING',username='notes_observer',password=c['redis_observer']) != '+PONG':
        raise RuntimeError('Observer failed after Redis restart')
    (BASE / 'access-done').touch()
    print('Dedicated DB observer + Redis ACL + Rabbit app/monitor/admin ready; anonymous Redis and guest disabled; ACL/AOF persist')

def private_ports():
    for key in ['pg','rabbit']:
        update(key,lambda s: s.setdefault('EndpointSpec',{}).update({'Ports':[]}))
    metadata_update('postgres',NAMES['pg'],{'externalPort':None})
    # Rabbit ports are separate Dokploy rows; remove only these known publishes.
    application_id = sql('SELECT "applicationId" FROM application WHERE "appName"=' + literal(NAMES['rabbit']) + ';').strip()
    tables = sql("SELECT table_name FROM information_schema.columns WHERE column_name='publishedPort' AND table_schema='public';").split()
    for table in tables:
        columns = sql("SELECT column_name FROM information_schema.columns WHERE table_name=" + literal(table) + ";").split()
        if 'applicationId' in columns:
            raw = sql('SELECT COALESCE(json_agg(row_to_json(p)),\'[]\'::json) FROM "' + table + '" p WHERE "applicationId"=' + literal(application_id) + ';')
            (BASE / ('dokploy-' + table + '-rabbit.json')).write_text(raw)
            sql('DELETE FROM "' + table + '" WHERE "applicationId"=' + literal(application_id) + ' AND "publishedPort" IN (5672,15672,61613);')
    # Docker ingress ports are removed rather than relying on UFW bypassed by Docker.
    for key in ['pg','rabbit','redis','kafka']:
        if inspect(key).get('Endpoint',{}).get('Spec',{}).get('Ports'):
            raise RuntimeError('Unexpected published broker port')
    (BASE / 'private-ports-done').touch()
    print('PostgreSQL, Redis, Kafka, RabbitMQ have no host/ingress port publishes; access via private SSH tunnels')

def be_rollout():
    settings = {'Parallelism':1,'Order':'stop-first','FailureAction':'pause'}
    metadata_update('application',NAMES['be'],{'updateConfigSwarm':settings})
    update('be',lambda s:s.update({'UpdateConfig':settings}))
    wait('be')
    print('BE rollout stop-first persisted; host-port collision removed')

STAGE = 'notesweb-verify-20261005'
STAGED = {key:'notesweb-verify-' + key for key in ['kafka','rabbit','pg','redis','be']}

def stage_setup():
    if not run(['docker','network','inspect',STAGE],check=False).returncode == 0:
        run(['docker','network','create','--internal',STAGE])
    snapshots = json.loads((BASE / 'before.json').read_text())
    c = creds()
    for key in ['kafka','rabbit']:
        volume = 'notesweb-verify-' + key + '-data'
        run(['docker','volume','create',volume])
        path = run(['docker','volume','inspect',volume,'--format','{{.Mountpoint}}']).stdout.strip()
        if not run(['docker','container','inspect',STAGED[key]],check=False).returncode == 0:
            run(['tar','-xpf',str(BASE / (key + '-data.tar')),'-C',path])
            if key == 'kafka':
                run(['chown','-R','1000:1000',path])
            args = ['docker','run','-d','--name',STAGED[key],'--network',STAGE,'--memory', '768m' if key=='kafka' else '512m', '--mount',f'type=volume,src={volume},dst=' + ('/tmp/kafka-logs' if key=='kafka' else '/var/lib/rabbitmq')]
            env = dict(x.split('=',1) for x in snapshots[key]['TaskTemplate']['ContainerSpec'].get('Env',[]))
            if key == 'kafka':
                env.update({'KAFKA_ADVERTISED_LISTENERS':'INTERNAL://' + STAGED[key] + ':9093',
                            'KAFKA_CONTROLLER_QUORUM_VOTERS':'1@' + STAGED[key] + ':9094',
                            'KAFKA_HEAP_OPTS':'-Xms256m -Xmx384m'})
            else:
                node = (BASE / 'rabbit-node').read_text().strip()
                args += ['--hostname',node.split('@',1)[1]]
                env['RABBITMQ_NODENAME'] = node
            for k,v in env.items():
                args += ['-e',k + '=' + v]
            args += [snapshots[key]['TaskTemplate']['ContainerSpec']['Image']]
            if key == 'rabbit':
                args += ['sh','-c','rabbitmq-plugins enable --offline rabbitmq_stomp; exec docker-entrypoint.sh rabbitmq-server']
            run(args)
    for key,image,environment,command in [
        ('pg','postgres:15',{'POSTGRES_USER':'postgres','POSTGRES_PASSWORD':c['pg_observer'],'POSTGRES_DB':'NoteApp'},[]),
        ('redis','redis:7',{},[])]:
        if run(['docker','container','inspect',STAGED[key]],check=False).returncode != 0:
            args = ['docker','run','-d','--name',STAGED[key],'--network',STAGE,'--memory','256m']
            for k,v in environment.items():
                args += ['-e',k + '=' + v]
            if key == 'redis':
                acl = BASE / 'stage-redis.acl'
                acl.write_text('user default off\nuser notes_app on #' + hashlib.sha256(c['redis_app'].encode()).hexdigest() + ' ~* &* +@all\n')
                # Test actual production ACLs, including command/key restrictions.
                acl.write_text((Path(run(['docker','volume','inspect','nnnoteswebapp-notesredis-ztd7pp-data','--format','{{.Mountpoint}}']).stdout.strip()) / 'notesweb-users.acl').read_text().replace('user default on','user default off'))
                os.chmod(acl,0o644)
                args += ['--mount',f'type=bind,src={acl},dst=/etc/redis/users.acl,readonly']
                command = ['redis-server','--aclfile','/etc/redis/users.acl']
            run(args + [image] + command)
    q = STAGED['rabbit']
    users = run(['docker','exec',q,'rabbitmqctl','list_users','--silent'],check=False)
    for _ in range(30):
        if users.returncode == 0:
            break
        time.sleep(2)
        users = run(['docker','exec',q,'rabbitmqctl','list_users','--silent'],check=False)
    for user,key in [('notes_app','rabbit_app'),('notes_admin','rabbit_admin')]:
        if user not in users.stdout:
            run(['docker','exec',q,'rabbitmqctl','add_user',user,c[key]])
        run(['docker','exec',q,'rabbitmqctl','set_permissions','-p','/',user,'.*','.*','.*'])
    run(['docker','exec',q,'rabbitmqctl','set_user_tags','notes_admin','administrator'])
    topics = run(['docker','exec',STAGED['kafka'],'/opt/kafka/bin/kafka-topics.sh','--bootstrap-server','localhost:9093','--list']).stdout
    queues = run(['docker','exec',q,'rabbitmqctl','list_queues','name','--silent']).stdout
    if 'note-updates' not in topics or 'reminder.mg.queue' not in queues:
        raise RuntimeError('Restored broker definitions missing')
    (BASE / 'restore-verified').touch()
    print('Isolated internal staging created; restored Kafka topic + Rabbit queues from backup archives verified')

def stage_be():
    c = creds()
    image = sys.argv[2]
    old = run(['docker','inspect',STAGED['be']],check=False)
    if old.returncode == 0:
        run(['docker','rm','-f',STAGED['be']])
    env = {'SPRING_PROFILES_ACTIVE':'docker','DB_URL':'jdbc:postgresql://' + STAGED['pg'] + ':5432/NoteApp',
        'DB_USERNAME':'postgres','DB_PASSWORD':c['pg_observer'],'REDIS_HOST':STAGED['redis'],'REDIS_PORT':'6379',
        'SPRING_REDIS_HOST':STAGED['redis'],'SPRING_REDIS_PORT':'6379',
        'SPRING_DATA_REDIS_USERNAME':'notes_app','SPRING_DATA_REDIS_PASSWORD':c['redis_app'],
        'KAFKA_BOOTSTRAP_SERVERS':STAGED['kafka'] + ':9093','RABBIT_HOST':STAGED['rabbit'],
        'RABBIT_PORT':'5672','RABBIT_STOMP_PORT':'61613','RABBIT_USERNAME':'notes_app','RABBIT_PASSWORD':c['rabbit_app'],
        'JWT_SECRET':secrets.token_hex(48),'JWT_EXPIRATION':'86400000','JWT_REFRESH':'604800000',
        'CLOUDINARY_CLOUD_NAME':'isolated-test','CLOUDINARY_API_KEY':'isolated-test','CLOUDINARY_API_SECRET':'isolated-test',
        'SERVER_PORT':'8081','INSTANCE_ID':'isolated-test'}
    args = ['docker','run','-d','--name',STAGED['be'],'--network',STAGE,'--memory','1g']
    for k,v in env.items():
        args += ['-e',k + '=' + v]
    run(args + [image])
    for _ in range(45):
        count = run(['docker','exec',STAGED['pg'],'psql','-U','postgres','-d','NoteApp','-Atc',"SELECT count(*) FROM information_schema.tables WHERE table_schema='public';"],check=False).stdout.strip()
        if count == '5':
            # Consumers begin asynchronously three seconds after ApplicationReadyEvent.
            time.sleep(8)
            print('Staging BE schema ready; credentialed brokers are isolated from production')
            return
        time.sleep(2)
    raise RuntimeError('Staging BE failed to initialize')

def staged_redis(*parts):
    return run(['docker','exec','-e','REDISCLI_AUTH=' + creds()['redis_app'],STAGED['redis'],'redis-cli','--user','notes_app','--raw',*map(str,parts)]).stdout.strip()

def staged_sql(query):
    return sql(query,'NoteApp',STAGED['pg'],'postgres').strip()

def poll(check, label, seconds=60):
    for _ in range(seconds):
        if check():
            return
        time.sleep(1)
    raise RuntimeError('Verification failed: ' + label)

def rabbit_api(method,path,data=None):
    import base64
    import urllib.request
    ip = api('GET','/containers/' + STAGED['rabbit'] + '/json')['NetworkSettings']['Networks'][STAGE]['IPAddress']
    auth = base64.b64encode(('notes_admin:' + creds()['rabbit_admin']).encode()).decode()
    request = urllib.request.Request('http://' + ip + ':15672/api/' + path,
        data=json.dumps(data).encode() if data is not None else None,method=method,
        headers={'Authorization':'Basic ' + auth,'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=10) as result:
        body = result.read()
        return json.loads(body) if body else None

def stage_test():
    import uuid
    uid = str(uuid.uuid4())
    username = 'staging_' + uid[:8]
    title = 'verify_' + uid[:8]
    # Use synthetic fixture identities only. No production DB or payloads enter staging.
    staged_sql('INSERT INTO users(id,username,email,password,role,status) VALUES (' + ','.join(map(literal,[uid,username,username+'@example.invalid','not-a-login-password','USER','SUCCESS'])) + ');')
    rid = staged_redis('XADD','notes:create:stream','*','username',json.dumps(username),'title',json.dumps(title),'content',json.dumps('synthetic staging content'))
    poll(lambda: staged_sql('SELECT count(*) FROM notes WHERE title=' + literal(title) + ';')=='1','Redis create persists note')
    nid = staged_sql('SELECT id FROM notes WHERE title=' + literal(title) + ';')
    poll(lambda:staged_redis('XPENDING','notes:create:stream','notes-group').splitlines()[0]=='0','Redis ACK after create')
    event = {'noteID':nid,'userID':uid,'username':username,'noteRequest':{'title':title+'_updated','content':'synthetic updated content'}}
    run(['docker','exec','-i',STAGED['kafka'],'/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:9093','--topic','note-updates'],json.dumps(event)+'\n')
    poll(lambda:staged_sql('SELECT title FROM notes WHERE id=' + literal(nid) + ';') == title+'_updated','Kafka updates DB')
    poll(lambda: '\t' not in run(['docker','exec',STAGED['kafka'],'/opt/kafka/bin/kafka-consumer-groups.sh','--bootstrap-server','localhost:9093','--describe','--group','note-update-group']).stdout and ' 0 ' in run(['docker','exec',STAGED['kafka'],'/opt/kafka/bin/kafka-consumer-groups.sh','--bootstrap-server','localhost:9093','--describe','--group','note-update-group']).stdout,'Kafka group committed',seconds=20)
    todo = str(uuid.uuid4())
    staged_sql('INSERT INTO to_do(id_list,heading,user_id,state,reminded,version,created_at,updated_at) VALUES (' + literal(todo) + ",'staging reminder'," + literal(uid) + ",'QUEUE',false,0,now(),now());")
    payload = {'todoID':todo,'trigger':'2026-10-05T00:00:00Z'}
    result = rabbit_api('POST','exchanges/%2F/reminder.mg.exchange/publish',{'properties':{'content_type':'application/json','delivery_mode':2},'routing_key':'reminder.ready.key','payload':json.dumps(payload),'payload_encoding':'string'})
    if not result.get('routed'):
        raise RuntimeError('Staging Rabbit message not routed')
    poll(lambda:staged_sql('SELECT state FROM to_do WHERE id_list=' + literal(todo) + ';')=='SENT','Rabbit reminder DB commit')
    poll(lambda:rabbit_api('GET','queues/%2F/reminder.mg.queue').get('messages_unacknowledged')==0,'Rabbit ACK after commit')
    (BASE / 'staging-happy.json').write_text(json.dumps({'user':uid,'note':nid,'title':title,'username':username,'todo':todo,'redis_record':rid}))
    print('PASS staging: Redis create+ACK, Kafka update+commit, Rabbit reminder DB SENT+ACK')

def stage_recovery():
    fixture = json.loads((BASE / 'staging-happy.json').read_text())
    title = fixture['title'] + '_recover'
    # Stop the worker first. Claim a message as a simulated crashed consumer to test PEL recovery.
    run(['docker','stop','-t','3',STAGED['be']])
    rid = staged_redis('XADD','notes:create:stream','*','username',json.dumps(fixture['username']),'title',json.dumps(title),'content',json.dumps('synthetic recovered content'))
    staged_redis('XREADGROUP','GROUP','notes-group','simulated-crashed-worker','COUNT','1','STREAMS','notes:create:stream','>')
    if staged_redis('XPENDING','notes:create:stream','notes-group').splitlines()[0] != '1':
        raise RuntimeError('Could not stage one pending message')
    event = {'noteID':fixture['note'],'userID':fixture['user'],'username':fixture['username'],'noteRequest':{'title':title+'_kafka','content':'restart recovery'}}
    run(['docker','exec','-i',STAGED['kafka'],'/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:9093','--topic','note-updates'],json.dumps(event)+'\n')
    # Past the current implementation's 10-second claim idle threshold.
    time.sleep(11)
    run(['docker','start',STAGED['be']])
    poll(lambda:staged_sql('SELECT count(*) FROM notes WHERE title=' + literal(title) + ';')=='1','Redis pending recovered after worker crash',seconds=90)
    poll(lambda:staged_redis('XPENDING','notes:create:stream','notes-group').splitlines()[0]=='0','Recovered Redis record ACK',seconds=20)
    poll(lambda:staged_sql('SELECT title FROM notes WHERE id=' + literal(fixture['note']) + ';')==title+'_kafka','Kafka queued update recovered',seconds=90)
    (BASE / 'staging-recovery-done').touch()
    print('PASS staging failure recovery: Redis pending claimed from crashed consumer; queued Kafka update processed after BE restart')

def deploy_be():
    image = sys.argv[2]
    if not (BASE / 'restore-verified').exists() or not (BASE / 'staging-recovery-done').exists():
        raise RuntimeError('Restore and staging recovery gates must pass before deployment')
    metadata_update('application',NAMES['be'],{'dockerImage':image,'updateConfigSwarm':{'Parallelism':1,'Order':'stop-first','FailureAction':'pause'}})
    def change(s):
        cs = s['TaskTemplate']['ContainerSpec']
        cs['Image'] = image
        env_set(s,{'SPRING_DATA_REDIS_HOST':NAMES['redis'],'SPRING_DATA_REDIS_PORT':'6379'})
        s['UpdateConfig'] = {'Parallelism':1,'Order':'stop-first','FailureAction':'pause'}
    metadata_env(NAMES['be'],{'SPRING_DATA_REDIS_HOST':NAMES['redis'],'SPRING_DATA_REDIS_PORT':'6379'})
    update('be',change)
    wait('be')
    print('Tested BE image deployed; Dokploy image + Redis host settings persisted')

def targets():
    print(json.dumps({key:address(key,port) for key,port in [('pg',5432),('redis',6379),('rabbit',15672)]}))

def verify():
    c = creds()
    if redis_command('PING',username='notes_app',password=c['redis_app']) != '+PONG':
        raise RuntimeError('Production Redis app authentication failed')
    if not str(redis_command('PING')).startswith('-NOAUTH'):
        raise RuntimeError('Redis anonymous authentication is still enabled')
    result = redis_command('ACL','DRYRUN','notes_observer','SET','notes:verification','blocked',username='notes_admin',password=c['redis_admin'])
    if 'no permissions' not in str(result).lower():
        raise RuntimeError('Observer write not rejected')
    clients = redis_command('CLIENT','LIST',username='notes_admin',password=c['redis_admin'])
    if clients.count('user=notes_app') < 3:
        raise RuntimeError('Production Redis workers not authenticated')
    queues = run(['docker','exec',cid(NAMES['rabbit']),'rabbitmqctl','list_queues','name','consumers','--silent']).stdout
    if 'reminder.mg.queue\t1' not in queues:
        raise RuntimeError('Rabbit reminder consumer missing')
    users = run(['docker','exec',cid(NAMES['rabbit']),'rabbitmqctl','list_users','--silent']).stdout
    if 'guest' in users:
        raise RuntimeError('Guest still exists')
    groups = run(['docker','exec',cid(NAMES['kafka']),'/opt/kafka/bin/kafka-consumer-groups.sh','--bootstrap-server','localhost:9093','--describe','--group','note-update-group']).stdout
    if 'consumer-note-update-group' not in groups:
        raise RuntimeError('Kafka consumer missing')
    for key in ['kafka','rabbit']:
        if not inspect(key)['Spec']['TaskTemplate']['ContainerSpec'].get('Mounts'):
            raise RuntimeError('Broker volume missing')
    print('PASS production: authenticated Redis workers, observer write denied, Rabbit guest removed + 1 reminder consumer, Kafka group active, both broker volumes present')

def stable_redis_endpoint():
    metadata_update('redis',NAMES['redis'],{'endpointSpecSwarm':{'Mode':'vip'}})
    update('redis',lambda s:s.setdefault('EndpointSpec',{}).update({'Mode':'vip'}))
    # Existing Lettuce clients retained the old DNSRR task address after Redis restart.
    update('be',lambda s:s['TaskTemplate'].update({'ForceUpdate':s['TaskTemplate'].get('ForceUpdate',0)+1}))
    wait('be')
    time.sleep(20)
    verify()

if __name__ == '__main__':
    try:
        {'prepare':prepare,'brokers':brokers,'fix-rabbit':fix_rabbit_hostname,'resume':resume,'fix-kafka':fix_kafka_volume,'access':access,'private-ports':private_ports,'be-rollout':be_rollout,'stage-setup':stage_setup,'stage-be':stage_be,'stage-test':stage_test,'stage-recovery':stage_recovery,'deploy-be':deploy_be,'targets':targets,'verify':verify,'stable-redis-endpoint':stable_redis_endpoint}[sys.argv[1]]()
    except Exception as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)
