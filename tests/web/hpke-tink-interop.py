"""Independent interoperability check. Run with Python Tink installed.
All keys generated here are disposable TEST keys, never application/user keys.
"""
import base64, hashlib, io, json, pathlib, subprocess, tempfile
import tink
from tink import hybrid, cleartext_keyset_handle
hybrid.register()
root=pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as tmp:
    rows=[]; handles=[]
    for name in ['DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM', 'DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM_RAW']:
        handle=tink.new_keyset_handle(getattr(hybrid.hybrid_key_templates,name))
        public=handle.public_keyset_handle()
        text=io.StringIO();public.write_no_secret(tink.JsonKeysetWriter(text))
        binary=io.BytesIO();public.write_no_secret(tink.BinaryKeysetWriter(binary))
        rows.append({'key':base64.b64encode(text.getvalue().encode()).decode(),'fingerprint':hashlib.sha256(binary.getvalue()).hexdigest().upper()})
        handles.append(handle)
    source=pathlib.Path(tmp)/'keys.json';source.write_text(json.dumps(rows))
    script=f'''import fs from 'node:fs';
import {{encryptToTink,keyFingerprint,protectPrivateData}} from {json.dumps((root/'web/private-send.mjs').as_uri())};
const rows=JSON.parse(fs.readFileSync(process.argv[1]));
const result=[];
for(const r of rows)result.push({{cipher:await encryptToTink(r.key,JSON.stringify({{text:'Привет с веба! 🖊️',replyText:'цитата',replySenderName:'Автор'}})),fingerprint:await keyFingerprint(r.key)}});
const data=await protectPrivateData('test',{{type:'PRIVATE',participantIds:['a','b']}},'a',{{text:'Закрытый текст',replyToText:'Секретная цитата',replyToSenderName:'Имя',replyToMessageId:'m'}},async uid=>rows[uid==='a'?0:1].key,async()=>{{}});
console.log(JSON.stringify({{result,data}}));'''
    output=json.loads(subprocess.check_output(['node','--input-type=module','-e',script,str(source)],text=True))
    for row,result,handle in zip(rows,output['result'],handles):
        assert row['fingerprint']==result['fingerprint'], 'Android fingerprint mismatch'
        dec=handle.primitive(hybrid.HybridDecrypt)
        plain=dec.decrypt(base64.b64decode(result['cipher']),b'yodo-e2e-v1')
        assert json.loads(plain)['text']=='Привет с веба! 🖊️'
        try:dec.decrypt(base64.b64decode(result['cipher']),b'wrong-context')
        except tink.TinkError:pass
        else:raise AssertionError('Wrong context accepted')
        damaged=bytearray(base64.b64decode(result['cipher']));damaged[-1]^=1
        try:dec.decrypt(bytes(damaged),b'yodo-e2e-v1')
        except tink.TinkError:pass
        else:raise AssertionError('Tampered ciphertext accepted')
    data=output['data']
    assert data['text']=='' and 'replyToText' not in data and 'replyToSenderName' not in data
    assert data['encryptedPayloadVersion']==2 and data['replyToMessageId']=='m'
    for uid,handle in zip(['a','b'],handles):
        plain=handle.primitive(hybrid.HybridDecrypt).decrypt(base64.b64decode(data['encByUid'][uid]),b'yodo-e2e-v1')
        assert json.loads(plain)=={'text':'Закрытый текст','replyText':'Секретная цитата','replySenderName':'Имя'}
print('PASS: WebCrypto → Tink decrypt (TINK and RAW), v2 payload for both phones, Android fingerprints, context and tamper rejection')
