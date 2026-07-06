import { Canvas } from '@react-three/fiber'
import { OrbitControls } from '@react-three/drei'

function RotatingBox() {
  return (
      <mesh rotation={[0.4, 0.4, 0]}>
        <boxGeometry args={[1.5, 1.5, 1.5]} />
        <meshStandardMaterial color="#7c3aed" />
      </mesh>
  )
}

function App() {
  return (
      <div style={{ width: '100vw', height: '100vh', background: '#0a0a0a' }}>
        <Canvas camera={{ position: [3, 3, 3] }}>
          <ambientLight intensity={0.5} />
          <directionalLight position={[5, 5, 5]} intensity={1} />
          <RotatingBox />
          <OrbitControls />
        </Canvas>
      </div>
  )
}

export default App