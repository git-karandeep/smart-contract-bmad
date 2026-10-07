// Initialize MongoDB with user and database
db = db.getSiblingDB('general-contracts');

db.createUser({
  user: 'connect2dockernet_db_user',
  pwd: 'connect2dockernet',
  roles: [
    { role: 'readWrite', db: 'general-contracts' }
  ]
});
