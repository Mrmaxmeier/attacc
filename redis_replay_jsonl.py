import redis
import json
import dateutil.parser
import time

r = redis.StrictRedis(host='localhost')

timescale = 1/10

prevtz = None

with open("events.jsonl", "r") as f:
	for line in f:
		data = json.loads(line)
		tz = dateutil.parser.isoparse(data["timestamp"])
		if prevtz:
			slp = tz - prevtz
			slp = slp.total_seconds() * timescale
			if slp > 0:
				time.sleep(slp)
		r.publish("events", line)
		prevtz = tz
