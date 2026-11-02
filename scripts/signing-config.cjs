const fs=require('node:fs');
for(const key of ['KEYSTORE_B64','STORE_PASSWORD','KEY_PASSWORD','KEY_ALIAS'])if(!process.env[key])throw Error('Missing protected signing secret: '+key+'. No production APK will be signed with a new key.');
fs.writeFileSync('release-key.jks',Buffer.from(process.env.KEYSTORE_B64,'base64'),{mode:0o600});
fs.writeFileSync('build.json',JSON.stringify({android:{release:{keystore:'release-key.jks',storePassword:process.env.STORE_PASSWORD,password:process.env.KEY_PASSWORD,alias:process.env.KEY_ALIAS,keystoreType:'jks',packageType:'apk'}}}),{mode:0o600});
