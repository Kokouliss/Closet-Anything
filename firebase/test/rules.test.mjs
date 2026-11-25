// Runs firestore.rules on the local emulator. Every write below has the same shape as the one
// the Android app sends (see core/data/.../firebase), so a rule that would break the app fails here.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  Bytes,
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

const CODE = 'ABCD2345';
const NEW_CODE = 'WXYZ6789';
const HOME = 'households/h1';
const ROOM = `${HOME}/rooms/r1`;
const SPACE = `${HOME}/spaces/s1`;
const THING = `${HOME}/things/t1`;

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-closet-anything',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(() => env.cleanup());

// A household owned by "owner" with one more member, one pending email invite, a room, a space and a thing.
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, HOME), {
      name: 'Home',
      ownerId: 'owner',
      memberIds: ['owner', 'member'],
      members: { owner: { name: 'Owner' }, member: { name: 'Member' } },
      inviteCode: CODE,
      invitedEmails: ['guest@example.com'],
      createdAt: new Date(),
    });
    await setDoc(doc(db, `invites/${CODE}`), { householdId: 'h1' });
    await setDoc(doc(db, ROOM), { name: 'My room', createdAt: new Date() });
    await setDoc(doc(db, SPACE), { roomId: 'r1', name: 'Under the bed', createdAt: new Date() });
    await setDoc(doc(db, THING), { name: 'Nike shoes', roomId: 'r1', spaceId: 's1', createdByName: 'Owner', createdAt: new Date() });
    await setDoc(doc(db, `${THING}/photo/full`), { data: Bytes.fromUint8Array(new Uint8Array(10)) });
  });
});

function as(uid, email, emailVerified = true) {
  const token = email ? { email, email_verified: emailVerified } : {};
  return env.authenticatedContext(uid, token).firestore();
}
const owner = () => as('owner', 'owner@example.com');
const member = () => as('member', 'member@example.com');
const stranger = () => as('stranger', 'stranger@example.com');
const guest = () => as('guest', 'guest@example.com');

function joinWithCode(db, uid, code) {
  return updateDoc(doc(db, HOME), {
    memberIds: arrayUnion(uid),
    [`members.${uid}`]: { name: 'New', inviteCode: code },
  });
}

function resetCode(db, extra = {}) {
  return writeBatch(db)
    .update(doc(db, HOME), { inviteCode: NEW_CODE, ...extra })
    .delete(doc(db, `invites/${CODE}`))
    .set(doc(db, `invites/${NEW_CODE}`), { householdId: 'h1' })
    .commit();
}

function newHousehold(db, ownerId, { withInvite = true } = {}) {
  const batch = writeBatch(db).set(doc(db, 'households/h2'), {
    name: 'Flat',
    ownerId,
    memberIds: [ownerId],
    members: { [ownerId]: { name: 'Me' } },
    inviteCode: NEW_CODE,
    invitedEmails: [],
    createdAt: serverTimestamp(),
  });
  if (withInvite) batch.set(doc(db, `invites/${NEW_CODE}`), { householdId: 'h2' });
  return batch.commit();
}

test('anyone signed in can create a household with its invite, but only as its own owner', async () => {
  await assertSucceeds(newHousehold(stranger(), 'stranger'));
  await assertFails(newHousehold(as('other'), 'stranger'));
  await assertFails(newHousehold(env.unauthenticatedContext().firestore(), 'stranger'));
  await assertFails(newHousehold(stranger(), 'stranger', { withInvite: false }));
  await assertFails(setDoc(doc(stranger(), `invites/${NEW_CODE}`), { householdId: 'h1' }));
});

test('members read their households with the query the app uses', async () => {
  const mine = (db, uid) => getDocs(query(collection(db, 'households'), where('memberIds', 'array-contains', uid)));
  await assertSucceeds(mine(member(), 'member'));
  await assertFails(mine(stranger(), 'member'));
  await assertFails(getDoc(doc(stranger(), HOME)));
});

test('an invited, verified email sees the invitation and nobody else does', async () => {
  const invitations = (db, email) =>
    getDocs(query(collection(db, 'households'), where('invitedEmails', 'array-contains', email)));
  await assertSucceeds(invitations(guest(), 'guest@example.com'));
  await assertFails(invitations(stranger(), 'guest@example.com'));
  await assertFails(invitations(as('guest', 'guest@example.com', false), 'guest@example.com'));
});

test('an invite code is looked up one at a time and cannot be listed', async () => {
  await assertSucceeds(getDoc(doc(stranger(), `invites/${CODE}`)));
  await assertFails(getDocs(collection(stranger(), 'invites')));
});

test('joining needs the current invite code and adds only the caller', async () => {
  await assertFails(joinWithCode(stranger(), 'stranger', 'AAAA2222'));
  await assertFails(joinWithCode(stranger(), 'other', CODE));
  await assertFails(
    updateDoc(doc(stranger(), HOME), {
      memberIds: arrayUnion('stranger'),
      'members.stranger': { name: 'New', inviteCode: CODE },
      name: 'Mine now',
    }),
  );
  await assertSucceeds(joinWithCode(stranger(), 'stranger', CODE));
  await assertSucceeds(getDoc(doc(stranger(), THING)));
});

test('the owner can reset the invite code and the old code stops working', async () => {
  await assertFails(resetCode(member()));
  await assertFails(updateDoc(doc(owner(), HOME), { inviteCode: NEW_CODE }));
  await assertSucceeds(resetCode(owner()));
  await assertFails(joinWithCode(stranger(), 'stranger', CODE));
  await assertSucceeds(joinWithCode(stranger(), 'stranger', NEW_CODE));
});

test('the owner can remove a member, who then loses access', async () => {
  const remove = (db) =>
    resetCode(db, { memberIds: arrayRemove('member'), 'members.member': deleteField() });
  await assertFails(remove(member()));
  await assertSucceeds(remove(owner()));
  await assertFails(getDoc(doc(member(), HOME)));
  await assertFails(getDoc(doc(member(), THING)));
});

test('only the owner can rename the household, and ownership cannot move', async () => {
  await assertSucceeds(updateDoc(doc(owner(), HOME), { name: 'Our flat' }));
  await assertFails(updateDoc(doc(member(), HOME), { name: 'Mine' }));
  await assertFails(updateDoc(doc(owner(), HOME), { ownerId: 'member' }));
  await assertFails(updateDoc(doc(owner(), HOME), { name: '' }));
});

test('members manage email invites, the invited accept or decline, strangers do nothing', async () => {
  const invite = (db) => updateDoc(doc(db, HOME), { invitedEmails: arrayUnion('friend@example.com') });
  await assertSucceeds(invite(member()));
  await assertFails(invite(stranger()));
  const accept = (db, uid, email) =>
    updateDoc(doc(db, HOME), {
      memberIds: arrayUnion(uid),
      [`members.${uid}`]: { name: 'Guest' },
      invitedEmails: arrayRemove(email),
    });
  await assertFails(accept(stranger(), 'stranger', 'guest@example.com'));
  await assertFails(accept(as('guest', 'guest@example.com', false), 'guest', 'guest@example.com'));
  await assertFails(updateDoc(doc(guest(), HOME), { invitedEmails: arrayRemove('friend@example.com') }));
  await assertSucceeds(accept(guest(), 'guest', 'guest@example.com'));
  await assertSucceeds(getDoc(doc(guest(), THING)));
});

test('a member can leave or rename themselves, the owner hands over, and nobody touches someone else', async () => {
  const leave = (db, uid) =>
    updateDoc(doc(db, HOME), { memberIds: arrayRemove(uid), [`members.${uid}`]: deleteField() });
  await assertFails(leave(owner(), 'owner'));
  await assertFails(leave(member(), 'owner'));
  await assertFails(updateDoc(doc(member(), HOME), { 'members.owner.name': 'Maria' }));
  await assertSucceeds(updateDoc(doc(member(), HOME), { 'members.member.name': 'Maria' }));
  await assertSucceeds(leave(member(), 'member'));
  const handOver = (db, to) =>
    updateDoc(doc(db, HOME), { ownerId: to, memberIds: arrayRemove('owner'), 'members.owner': deleteField() });
  await assertFails(handOver(owner(), 'stranger'));
  await assertFails(handOver(owner(), 'owner'));
});

test('only the owner can delete the household', async () => {
  const remove = (db) =>
    writeBatch(db).delete(doc(db, `invites/${CODE}`)).delete(doc(db, HOME)).commit();
  await assertFails(remove(member()));
  await assertSucceeds(remove(owner()));
});

test('members read and edit rooms, spaces and things, strangers cannot', async () => {
  for (const path of [`${HOME}/rooms`, `${HOME}/spaces`, `${HOME}/things`]) {
    await assertSucceeds(getDocs(collection(member(), path)));
    await assertFails(getDocs(collection(stranger(), path)));
  }
  const room = { name: 'Storage', createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(member(), `${HOME}/rooms/r2`), room));
  await assertFails(setDoc(doc(stranger(), `${HOME}/rooms/r3`), room));
  await assertSucceeds(setDoc(doc(member(), `${HOME}/spaces/s2`), { roomId: 'r1', name: 'Wardrobe', createdAt: serverTimestamp() }));
  const box = { name: 'Jewellery box', roomId: 'r1', spaceId: 's1', box: true, createdByName: 'Member', createdAt: serverTimestamp() };
  await assertSucceeds(setDoc(doc(member(), `${HOME}/things/b1`), box));
  await assertSucceeds(
    setDoc(doc(member(), `${HOME}/things/t2`), {
      name: 'Ruby ring',
      roomId: 'r1',
      spaceId: 's1',
      boxId: 'b1',
      note: 'From grandma',
      quantity: 1,
      tags: ['gift'],
      createdByName: 'Member',
      createdAt: serverTimestamp(),
    }),
  );
  await assertFails(setDoc(doc(stranger(), `${HOME}/things/t3`), box));
  await assertSucceeds(updateDoc(doc(member(), THING), { name: 'Black Nike shoes', quantity: 2, roomId: 'r2', spaceId: 's2' }));
  await assertSucceeds(updateDoc(doc(member(), ROOM), { name: 'Bedroom' }));
  await assertSucceeds(deleteDoc(doc(member(), SPACE)));
  await assertFails(deleteDoc(doc(stranger(), THING)));
});

test('rooms, spaces and things with a wrong shape are rejected', async () => {
  await assertFails(setDoc(doc(member(), `${HOME}/rooms/r2`), { name: '', createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(member(), `${HOME}/spaces/s2`), { name: 'Wardrobe', createdAt: serverTimestamp() }));
  const thing = (extra) => setDoc(doc(member(), `${HOME}/things/t2`), {
    name: 'Ring', roomId: 'r1', spaceId: 's1', createdByName: 'Member', createdAt: serverTimestamp(), ...extra,
  });
  await assertFails(thing({ name: 'x'.repeat(61) }));
  await assertFails(thing({ quantity: 0 }));
  await assertFails(thing({ quantity: 1000 }));
  await assertFails(thing({ note: 'x'.repeat(501) }));
  await assertFails(thing({ tags: Array(11).fill('tag') }));
  await assertFails(thing({ box: 'yes' }));
  await assertFails(thing({ extra: 1 }));
  await assertFails(thing({ thumbnail: Bytes.fromUint8Array(new Uint8Array(60001)) }));
  await assertSucceeds(thing({ thumbnail: Bytes.fromUint8Array(new Uint8Array(100)) }));
});

test('photos are stored and read by members only, within the size limit', async () => {
  const photo = (db, size) => setDoc(doc(db, `${HOME}/things/t1/photo/full`), { data: Bytes.fromUint8Array(new Uint8Array(size)) });
  await assertSucceeds(getDoc(doc(member(), `${THING}/photo/full`)));
  await assertFails(getDoc(doc(stranger(), `${THING}/photo/full`)));
  await assertSucceeds(photo(member(), 1000));
  await assertFails(photo(stranger(), 1000));
  await assertFails(photo(member(), 800001));
  await assertFails(setDoc(doc(member(), `${HOME}/things/t1/photo/other`), { data: Bytes.fromUint8Array(new Uint8Array(10)) }));
  await assertFails(setDoc(doc(member(), `${HOME}/things/t1/photo/full`), { data: 'text' }));
  await assertSucceeds(deleteDoc(doc(member(), `${THING}/photo/full`)));
});
