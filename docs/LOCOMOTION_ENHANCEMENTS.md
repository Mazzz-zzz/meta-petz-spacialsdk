# Pet Locomotion Enhancement Ideas

## Current Implementation
- Point-to-move with controller/hand tracking
- Smooth slerp rotation (AnimationsSample-style)
- Walk animation during movement, wag at idle
- MRUK raycasting for surface detection

---

## Enhancement Ideas

### 1. Animation Blending
- **Idle variations**: Randomly alternate between wag, sit, and look-around animations
- **Walk-to-run transition**: Speed up animation when distance is far, slow walk for nearby targets
- **Start/stop animations**: Add "get up" before walking, "settle down" after arriving

### 2. Pathfinding & Obstacle Avoidance
- **MRUK furniture avoidance**: Use scene anchors to path around tables/chairs
- **Jump over small obstacles**: Detect low objects and play jump animation
- **Climb on furniture**: Allow pet to hop onto couches/beds when pointed at them

### 3. Behavioral AI
- **Autonomous wandering**: Pet explores room when idle for too long
- **Follow mode**: Pet follows player at a distance (like real pet)
- **Attention seeking**: Pet moves to be in player's field of view if ignored
- **Rest behavior**: Pet finds a cozy spot (near furniture) to rest after activity

### 4. Enhanced Interactions
- **Pet reactions to gaze**: Pet looks at player when being watched
- **Proximity responses**: Different animations when player is near vs far
- **Voice command integration**: "Come here", "Stay", "Sit" voice triggers
- **Hand gesture recognition**: Wave to call pet, point to direct

### 5. Environmental Awareness
- **Surface-appropriate behavior**: Different walk style on floor vs furniture
- **Boundary respect**: Pet stays within room bounds detected by MRUK
- **Light/shadow awareness**: Pet seeks sunny spots (window areas)
- **Time-of-day behavior**: More active during day, sleepy at night

### 6. Physics & Realism
- **Momentum/inertia**: Pet doesn't stop instantly, slight slide
- **Head tracking**: Pet's head follows player independent of body
- **Ear/tail physics**: Secondary motion on ears and tail
- **Footstep sounds**: Audio feedback synced with walk animation

### 7. Multi-Pet Interactions
- **Pet-to-pet awareness**: Multiple pets acknowledge each other
- **Play together**: Pets chase or play when near each other
- **Territory behavior**: Pets have preferred spots in room

### 8. Emotional State System
- **Happiness meter affects animations**: Happy = bouncy walk, sad = slow trudge
- **Excitement buildup**: More energetic after being petted/fed
- **Tired state**: Slower movement after lots of activity
- **Visual indicators**: Particle effects (hearts, stars) for emotional states

### 9. Advanced Movement
- **Terrain adaptation**: Adjust height when walking on uneven surfaces
- **Stair climbing**: Detect and animate stair traversal
- **360° awareness**: Pet can walk backwards or sideways briefly
- **Smooth path curves**: Bezier curves instead of straight lines for natural paths

### 10. Social Features
- **Photo mode poses**: Special poses when camera is detected
- **Tricks on command**: Spin, roll over, play dead animations
- **Greeting animations**: Excited reaction when player returns to app
- **Shared pet visits**: See friends' pets in your space (multiplayer)

---

## Priority Recommendations

**High Impact, Lower Effort:**
1. Idle animation variations
2. Head tracking toward player
3. Proximity-based reactions
4. Footstep audio

**High Impact, Higher Effort:**
1. Autonomous wandering with MRUK
2. Follow mode
3. Obstacle avoidance pathfinding
4. Emotional state system

---

## Technical Notes
- Use existing `ANIM_IDLE`, `ANIM_WALK`, `ANIM_WAG`, `ANIM_WALKLOOP` tracks
- Leverage MRUK anchors for furniture detection
- PlayerBodyAttachmentSystem for player position/gaze
- Consider ECS components for behavioral states
