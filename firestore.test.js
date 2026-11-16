const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const testTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_EMAIL = "rainytalesstudio@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read stories, users, or chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("stories").where("isActive", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("chats").where("userId", "==", ALICE_UID).get());
});

test("Admin can create/edit/delete stories; normal user cannot", async () => {
  const adminDb = testEnv.authenticatedContext("admin_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  const storyPayload = {
    title: "One Boy In Mystic Land",
    description: "An adventure across a hidden kingdom.",
    systemPrompt: "Adventure only, 3 options A/B/C.",
    coverImage: "preset:mystic_realm",
    genre: "Fantasy",
    openingLine: "Aap ek anjaan jungle me jaagte hain...",
    isActive: true,
    createdAt: testTimestamp(),
    updatedAt: testTimestamp(),
  };

  await assertFails(aliceDb.collection("stories").doc("story_1").set(storyPayload));
  await assertSucceeds(adminDb.collection("stories").doc("story_1").set(storyPayload));
  await assertSucceeds(aliceDb.collection("stories").where("isActive", "==", true).get());
  await assertFails(aliceDb.collection("stories").get()); // unfiltered list denied for non-admin
});

test("Email spoofing test: unverified admin email is rejected", async () => {
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(spoofedDb.collection("stories").doc("story_spoof").set({
    title: "Spoof",
    description: "Spoof",
    systemPrompt: "Spoof",
    coverImage: "preset:mystic_realm",
    isActive: true,
    createdAt: testTimestamp(),
    updatedAt: testTimestamp(),
  }));
});

test("User profile isolation and shadow update rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    name: "Vansh",
    createdAt: testTimestamp(),
    updatedAt: testTimestamp(),
  }));

  // Shadow update / privilege escalation rejected
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({
    userId: ALICE_UID,
    name: "Vansh",
    isAdmin: true,
    createdAt: testTimestamp(),
    updatedAt: testTimestamp(),
  }));

  // Cross-user read rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Chats are strictly isolated per userId and storyId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext("admin_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const chatId = `${ALICE_UID}_story_1`;
  // Alice can observe/get her own chat before first message exists; Bob cannot
  await assertSucceeds(aliceDb.collection("chats").doc(chatId).get());
  await assertFails(bobDb.collection("chats").doc(chatId).get());

  await assertSucceeds(aliceDb.collection("chats").doc(chatId).set({
    userId: ALICE_UID,
    storyId: "story_1",
    storyTitle: "One Boy In Mystic Land",
    messages: [{ id: "m1", role: "model", content: "Start!", timestampMs: 1000 }],
    summary: "",
    messageCount: 1,
    createdAt: testTimestamp(),
    updatedAt: testTimestamp(),
  }));

  // Alice can query her own chats
  await assertSucceeds(aliceDb.collection("chats").where("userId", "==", ALICE_UID).get());
  // Bob cannot read Alice's chat or query all chats
  await assertFails(bobDb.collection("chats").doc(chatId).get());
  await assertFails(bobDb.collection("chats").get());
  // Admin can view Alice's chat
  await assertSucceeds(adminDb.collection("chats").doc(chatId).get());
  await assertSucceeds(adminDb.collection("chats").get());
});
