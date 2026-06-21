import sys
import os

# Agrega el directorio raíz de micro-coincidencias al path para que
# los imports del tipo "from app.xxx import yyy" funcionen en los tests
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
