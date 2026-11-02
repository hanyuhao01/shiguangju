const fs=require('node:fs'),path=require('node:path');
const target='platforms/android/app/src/test/java/com/shiguangju';fs.mkdirSync(target,{recursive:true});
for(const f of fs.readdirSync('tests/android'))fs.copyFileSync(path.join('tests/android',f),path.join(target,f));
fs.appendFileSync('platforms/android/app/build.gradle',`
android { testOptions { unitTests.includeAndroidResources = true } }
dependencies {
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.robolectric:robolectric:4.14.1'
}
`);
