"""Exact offline probe profiles. No firmware download or device access."""
import hashlib

HASH114 = 'fa869de17b99836d5962d0566e93f543765194be2885265df514cd9a27dfc8aa'
HASH115 = '5dc4b6088c2bbd8f718387ea7f6a419b368bc9975b6b588bc55ccce2991c5970'
MAP115 = {
    0x0800e384: 0x0800e3c8, 0x0800c970: 0x0800c996,
    0x0800f35a: 0x0800f3a6, 0x0800f3cc: 0x0800f418,
    0x0800878c: 0x080087ec, 0x0800e0a2: 0x0800e0e0,
    0x0800c29c: 0x0800c266,
}

def address_profile(blob):
    digest = hashlib.sha256(blob).hexdigest()
    assert digest in (HASH114, HASH115), 'Only exact reviewed 1.1.4/1.1.5 binaries'
    def address(value):
        base = value & ~1
        return MAP115.get(base, base) + (value & 1) if digest == HASH115 else value
    return address
