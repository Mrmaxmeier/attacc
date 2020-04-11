import redis

r = redis.Redis(host='localhost', port=6379, db=0)

with open('events.jsonl', 'r') as f:
    events = f.readlines()

for event in events:
    r.publish('events', event)
