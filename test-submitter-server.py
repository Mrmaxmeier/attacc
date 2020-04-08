from pwn import *

# flag format: FLAG\{[A-Za-z0-9-_]{32}\}
responses = {
	"FLAG{VAL_": "[OK]",
	"FLAG{INV_": "[ERR] Invalid flag",
	"FLAG{EXP_": "[ERR] Expired",
	"FLAG{DUP_": "[ERR] Already submitted",
	"FLAG{NOP_": "[ERR] Can't submit flag from NOP team",
	"FLAG{OWN_": "[ERR] This is your own flag",
}

l = listen(port=31337)
while True:
	try:
		entry = l.readline().decode().strip()
	except Exception as e:
		l.close()
		l = listen(port=31337)
		continue
	def process(flag):
		for k, v in responses.items():
			if entry.startswith(k):
				return v
		return "[ERR] Invalid format"
	l.sendline(process(entry))
