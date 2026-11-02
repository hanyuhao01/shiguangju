const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
test('test APK preparation uses widget version and preserves the real source config',()=>{
  const root=path.resolve(__dirname,'..'),before=fs.readFileSync(path.join(root,'config.xml'),'utf8'),tmp=path.join(root,'artifacts','prepare-'+Date.now());
  fs.mkdirSync(tmp,{recursive:true});fs.mkdirSync(path.join(tmp,'scripts'));
  for(const f of ['config.xml','index.html','core.js','v2.js','v2.css','manifest.json','icon-192.png','icon-512.png'])fs.copyFileSync(path.join(root,f),path.join(tmp,f));
  fs.copyFileSync(path.join(root,'scripts/prepare.cjs'),path.join(tmp,'scripts/prepare.cjs'));
  cp.execFileSync(process.execPath,[path.join(tmp,'scripts/prepare.cjs'),'--test'],{cwd:tmp,windowsHide:true});
  const actual=fs.readFileSync(path.join(tmp,'config.xml'),'utf8'),version=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8')).version,code=before.match(/android-versionCode="(\d+)"/)[1];
  assert.ok(actual.includes('id="com.shiguangju.test.v'+code+'"'));assert.ok(actual.includes('<name>拾光橘 V'+version+' 测试</name>'));
  assert.equal(fs.readFileSync(path.join(root,'config.xml'),'utf8'),before);assert.equal(fs.readFileSync(path.join(tmp,'www/core.js'),'utf8'),fs.readFileSync(path.join(root,'core.js'),'utf8'));
});
