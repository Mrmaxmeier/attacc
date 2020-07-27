import redis

r = redis.Redis(host='localhost', port=6379, db=0)

with open('events.jsonl', 'r') as f:
    for event in f:
        r.publish('events', event)

