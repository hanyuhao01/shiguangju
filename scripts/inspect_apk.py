"""Read APK binary XML and v2/v3 certificates without executing APK code."""
import sys, struct, zipfile, hashlib, json
from cryptography import x509
from cryptography.hazmat.primitives import hashes

def inspect(path):
    data = open(path, 'rb').read()
    z = zipfile.ZipFile(path)
    def xml(name):
        b=z.read(name); strings=[]; out=[]; off=8
        while off<len(b):
            typ,head,size=struct.unpack_from('<HHI',b,off)
            if typ==1:
                count,_,flags,start,_=struct.unpack_from('<IIIII',b,off+8)
                def length(p,utf8):
                    v=b[p] if utf8 else struct.unpack_from('<H',b,p)[0]; p+=1 if utf8 else 2
                    high=0x80 if utf8 else 0x8000
                    if v&high:
                        v=((v&~high)<<(8 if utf8 else 16)) | (b[p] if utf8 else struct.unpack_from('<H',b,p)[0]); p+=1 if utf8 else 2
                    return v,p
                for i in range(count):
                    p=off+start+struct.unpack_from('<I',b,off+head+i*4)[0]
                    utf=bool(flags&256)
                    if utf: _,p=length(p,True)
                    n,p=length(p,utf); strings.append(b[p:p+n*(1 if utf else 2)].decode('utf-8' if utf else 'utf-16le'))
            elif typ==0x102:
                ns,nm,ast,asz,ac=struct.unpack_from('<IIHHH',b,off+16); attrs={}
                for i in range(ac):
                    p=off+16+ast+i*asz; _,key,raw,_,_,dt,val=struct.unpack_from('<IIIHBBI',b,p)
                    attrs[strings[key]]=strings[raw] if raw!=0xffffffff else (strings[val] if dt==3 else val)
                out.append({'tag':strings[nm],'attributes':attrs})
            if size<8: raise ValueError('invalid chunk')
            off+=size
        return out
    # APK signing block immediately before central directory.
    eocd=data.rfind(b'PK\x05\x06'); cd=struct.unpack_from('<I',data,eocd+16)[0]; certs=[]
    def fields(b):
        pos=0
        while pos<len(b):
            n=struct.unpack_from('<I',b,pos)[0]; pos+=4; yield b[pos:pos+n]; pos+=n
    if data[cd-16:cd]==b'APK Sig Block 42':
        size=struct.unpack_from('<Q',data,cd-24)[0]; p=cd-size-8+8
        while p<cd-24:
            n=struct.unpack_from('<Q',data,p)[0]; ident=struct.unpack_from('<I',data,p+8)[0]; v=data[p+12:p+8+n]; p+=8+n
            if ident in (0x7109871a,0xf05368c0):
                for signer in fields(next(fields(v))):
                    signed=next(fields(signer)); parts=list(fields(signed))
                    for der in fields(parts[1]):
                        c=x509.load_der_x509_certificate(der); fp=c.fingerprint(hashes.SHA256()).hex()
                        if fp not in [c['sha256'] for c in certs]: certs.append({'sha256':fp,'subject':c.subject.rfc4514_string()})
    html=z.read('assets/www/index.html')
    return {'sha256':hashlib.sha256(data).hexdigest(),'certificates':certs,'manifest':xml('AndroidManifest.xml'),'cordovaConfig':xml('res/xml/config.xml'),'webFiles':[n for n in z.namelist() if n.startswith('assets/www/')], 'htmlSha256':hashlib.sha256(html).hexdigest(),'htmlMatchesRepository':html==open('index.html','rb').read()}
if __name__=='__main__': print(json.dumps(inspect(sys.argv[1]),ensure_ascii=False,indent=2))
