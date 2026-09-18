import { useEffect, useState } from 'react';
type Me = { authenticated: boolean; email: string | null };
export function AuthPanel() {
  const [me,setMe]=useState<Me>({authenticated:false,email:null});const [mode,setMode]=useState<'login'|'register'>('login');
  const [email,setEmail]=useState('');const [password,setPassword]=useState('');const [error,setError]=useState('');const [busy,setBusy]=useState(false);
  useEffect(()=>{fetch('/api/v1/auth/me').then(response=>response.json()).then(setMe).catch(()=>setError('Account service unavailable. Guest tools still work.'));},[]);
  async function submit(){
    setBusy(true);setError('');try{const response=await fetch(`/api/v1/auth/${mode}`,{method:'POST',headers:{'Content-Type':'application/json','X-Requested-With':'docflow'},body:JSON.stringify({email,password})});
      const result=await response.json().catch(()=>({}));if(!response.ok)throw new Error(result.detail||`${mode==='login'?'Login':'Registration'} failed.`);setMe(result);setPassword('');}
    catch(failure){setError(failure instanceof Error?failure.message:'Account request failed.');}finally{setBusy(false);}
  }
  async function logout(){
    setBusy(true);setError('');try{const response=await fetch('/api/v1/auth/logout',{method:'POST',headers:{'X-Requested-With':'docflow'}});if(!response.ok)throw new Error('Logout failed.');setMe(await response.json());}
    catch(failure){setError(failure instanceof Error?failure.message:'Logout failed.');}finally{setBusy(false);}
  }
  return <section className="account" aria-labelledby="account-title"><h2 id="account-title">Optional account</h2>
    {me.authenticated?<><p>Signed in as <strong>{me.email}</strong>. Jobs submitted while signed in are available after login on another browser.</p><button disabled={busy} onClick={()=>void logout()}>Log out</button></>
    :<><p>You can continue as a guest. Create an account only if you want persistent, account-scoped job history.</p>
      <div className="auth-tabs"><button className={mode==='login'?'':'secondary'} onClick={()=>setMode('login')}>Log in</button><button className={mode==='register'?'':'secondary'} onClick={()=>setMode('register')}>Register</button></div>
      <label>Email <input type="email" autoComplete="email" value={email} onChange={event=>setEmail(event.target.value)} /></label>
      <label>Password <input type="password" autoComplete={mode==='login'?'current-password':'new-password'} value={password} minLength={10} maxLength={128} onChange={event=>setPassword(event.target.value)} /></label>
      <button disabled={busy||!email||!password} onClick={()=>void submit()}>{busy?'Please wait…':mode==='login'?'Log in':'Create account'}</button>
      <p className="hint">Password: 10–128 characters with at least one letter and one number. Existing guest jobs are not moved into a new account.</p></>}
    {error&&<p className="error" role="alert">{error}</p>}
  </section>;
}
