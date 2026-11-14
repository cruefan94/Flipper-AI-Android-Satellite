"""Release-network policy: loopback HTTP only, no remote cleartext expansion."""
from pathlib import Path
import xml.etree.ElementTree as ET
import unittest

ROOT=Path(__file__).resolve().parents[1]
SECURITY=ROOT/"app/src/main/res/xml/local_model_security.xml"

class NetworkSecurityReleaseTest(unittest.TestCase):
    def test_loopback_only_and_no_subdomains(self):
        root=ET.parse(SECURITY).getroot()
        base=root.find("base-config")
        self.assertEqual(base.attrib.get("cleartextTrafficPermitted"),"false")
        configs=root.findall("domain-config")
        self.assertEqual(len(configs),1)
        self.assertEqual(configs[0].attrib.get("cleartextTrafficPermitted"),"true")
        domains=configs[0].findall("domain")
        self.assertEqual(len(domains),1)
        self.assertEqual(domains[0].text.strip(),"127.0.0.1")
        self.assertEqual(domains[0].attrib.get("includeSubdomains"),"false")

if __name__=="__main__": unittest.main()
