#!/bin/bash
set -e

# Initialize MongoDB replica set
mongosh --host mongodb --port 27017 -u admin -p admin --authenticationDatabase admin --eval "
rs.initiate({
  _id: 'rs0',
  members: [
    { _id: 0, host: 'mongodb:27017' }
  ]
})
"

# Wait for replica set to become healthy
mongosh --host mongodb --port 27017 -u admin -p admin --authenticationDatabase admin --eval "
while (rs.status().myState !== 1) {
  print('Waiting for replica set to become primary...');
  sleep(1000);
}
print('Replica set initialized successfully');
"
