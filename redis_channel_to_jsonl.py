import redis
import json

r = redis.StrictRedis(host='localhost')
p = r.pubsub(ignore_subscribe_messages=True)
p.subscribe('events')

with open("events.jsonl", "w") as f:
	for message in p.listen():
		data = message["data"].decode()
		line = json.dumps(json.loads(data))
		print(line)
		f.write(line + "\n")
